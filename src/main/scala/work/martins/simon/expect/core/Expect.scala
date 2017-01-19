package work.martins.simon.expect.core

import java.nio.charset.Charset

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.Settings
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core.Context.{ChangeToNewExpect, Continue, Terminate}

/**
  * @define type `Expect`
  */
final class Expect[R](val command: Seq[String], val defaultValue: R, val settings: Settings = new Settings())
                     (val expectBlocks: ExpectBlock[R]*) extends LazyLogging {
  def this(command: Seq[String], defaultValue: R, config: Config)(expects: ExpectBlock[R]*) = {
    this(command, defaultValue, new Settings(config))(expects: _*)
  }

  def this(command: String, defaultValue: R, settings: Settings)(expectBlocks: ExpectBlock[R]*) = {
    this(splitBySpaces(command), defaultValue, settings)(expectBlocks: _*)
  }

  def this(command: String, defaultValue: R, config: Config)(expectBlocks: ExpectBlock[R]*) = {
    this(command, defaultValue, new Settings(config))(expectBlocks: _*)
  }

  def this(command: String, defaultValue: R)(expectBlocks: ExpectBlock[R]*) = {
    this(command, defaultValue, new Settings())(expectBlocks: _*)
  }

  require(command.nonEmpty, "Expect must have a command to run.")

  def run(timeout: FiniteDuration = settings.timeout, charset: Charset = settings.charset,
          redirectStdErrToStdOut: Boolean = settings.redirectStdErrToStdOut)
         (implicit ex: ExecutionContext): Future[R] = {
    run(RichProcess(command, timeout, charset, redirectStdErrToStdOut))
  }
  def run(settings: Settings)(implicit ex: ExecutionContext): Future[R] = {
    run(RichProcess(command, settings))
  }
  def run(process: RichProcess)(implicit ex: ExecutionContext): Future[R] = {
    val context = Context(
      id = hashCode(),
      value = defaultValue,
      executionAction = Continue,
      redirectStdErrToStdOut = process.redirectStdErrToStdOut
    )
    
    logger.info(context.withId(command.mkString("\"", " ", "\"")))
    
    def successful(context: Context[R]): Future[R] = {
      logger.info(context.withId(s"Finished returning: ${context.value}"))
      Future.successful(context.value)
    }
    
    def innerRun(expectBlocks: Seq[ExpectBlock[R]], context: Context[R]): Future[R] = {
      expectBlocks.headOption.map { headExpectBlock =>
        //We still have expect blocks to run
        val result = headExpectBlock.run(process, context).flatMap { innerContext =>
          innerContext.executionAction match {
            case Continue =>
              //Continue with the remaining expect blocks
              innerRun(expectBlocks.tail, innerContext)
            case Terminate =>
              //Simply terminate with the innerContext
              successful(innerContext)
            case ChangeToNewExpect(newExpect) =>
              process.destroy()
              newExpect.asInstanceOf[Expect[R]].run(process.withCommand(newExpect.command))
          }
        }
        //If we get an exception while running the head expect block we want to make sure the rich process is destroyed.
        result onComplete (_ => process.destroy())
        result
      } getOrElse {
        //No more expect blocks. We just return the current context (which contains the final result/value)
        successful(context)
      }
    }
  
    innerRun(expectBlocks, context)
  }
  
  /** Creates a new $type by applying a function to the returned result of this $type.
    * @group Transformations
    */
  def map[T](f: R => T): Expect[T] = {
    // We could implement map using transform:
    //  transform(PartialFunction.empty[R, Expect[T]]){ case r => f(r) }
    // But it would be more inefficient since an ActionReturningAction would be created when it wasn't necessary.
    new Expect(command, f(defaultValue), settings)(expectBlocks.map(_.map(f)):_*)
  }
  /** Creates a new $type by applying a function to the returned result of this $type, and returns the result
    * of the function as the new $type.
    * @group Transformations
    */
  def flatMap[T](f: R => Expect[T]): Expect[T] = {
    // We could implement flatMap using transform:
    //  transform{ case r => f(r) }(PartialFunction.empty[R, T])
    // But it would be more inefficient since an ActionReturningAction would be created when it wasn't necessary.
    new Expect(command, f(defaultValue).defaultValue, settings)(expectBlocks.map(_.flatMap(f)):_*)
  }
  /**
    * Transform this $type result using the following strategy:
    *  - if `flatMapPF` `isDefinedAt` for this expect result then the result is flatMapped using flatMapPF.
    *  - otherwise, if `mapPF` `isDefinedAt` for this expect result then the result is mapped using mapPF.
    *  - otherwise a NoSuchElementException is thrown where the result would be expected.
    *
    * This function is very useful when we need to flatMap this $type for some values of its result type and map
    * this $type for some other values of its result type.
    *
    * To ensure you don't get NoSuchElementException you should take special care in ensuring:
    * {{{ domain(flatMapPF) ∪ domain(mapPF) == domain(R) }}}
    * Remember that in the domain of R the `defaultValue` is also included.
    *
    * @example {{{
    * def countFilesInFolder(folder: String): Expect[Either[String, Int]] = {
    *   val e = new Expect(s"ls -1 $$folder", Left("unknownError"): Either[String, Int])(
    *     ExpectBlock(
    *       StringWhen("access denied")(
    *         Returning(Left("Access denied"))
    *       ),
    *       RegexWhen("(?s)(.*)".r)(
    *         ReturningWithRegex(_.group(1).split("\n").length)
    *       )
    *     )
    *   )
    *   e
    * }
    *
    * def ensureFolderIsEmpty(folder: String): Expect[Either[String, Unit]] = {
    *   countFilesInFolder(folder).transform {
    *     case Right(numberOfFiles) =>
    *       Expect(s"rm -r $$folder", Left("unknownError"): Either[String, Unit])(
    *         ExpectBlock(
    *           StringWhen("access denied")(
    *             Returning(Left("Access denied"))
    *           ),
    *           EndOfFileWhen(
    *             Returning(())
    *           )
    *         )
    *       )
    *   }{
    *     case Left(l) => Left(l)
    *     case Right(numberOfFiles) if numberOfFiles == 0 => Right(())
    *   }
    * }
    * }}}
    *
    * @param flatMapPF the function that will be applied when a flatMap is needed.
    * @param mapPF the function that will be applied when a map is needed.
    * @tparam T the type of the returned $type.
    * @return a new $type whose result is either flatMapped or mapped according to whether flatMapPF or
    *         mapPF is defined for the given result.
    * @group Transformations
    */
  def transform[T](flatMapPF: PartialFunction[R, Expect[T]])(mapPF: PartialFunction[R, T]): Expect[T] = {
    def notDefined(r: R): T = throw new NoSuchElementException(s"Expect.transform neither flatMapPF nor mapPF are defined at $r (the Expect default value)")

    val newDefaultValue = flatMapPF.andThen(_.defaultValue).applyOrElse(defaultValue, { r: R =>
      mapPF.applyOrElse(r, notDefined)
    })

    new Expect[T](command, newDefaultValue, settings)(expectBlocks.map(_.transform(flatMapPF)(mapPF)):_*)
  }
  /**
    * @group Transformations
    */
  def flatten[T](implicit ev: R <:< Expect[T]): Expect[T] = flatMap(ev)
  /**
   * @group Transformations
   */
  def filter(p: R => Boolean): Expect[R] = map { r =>
    if (p(r)) r else throw new NoSuchElementException(s"Expect.filter predicate is not satisfied for: $r.")
  }
  /** Used by for-comprehensions.
    * @group Transformations
    */
  def withFilter(p: R => Boolean): Expect[R] = filter(p)
  /**
    * @group Transformations
    */
  def collect[T](pf: PartialFunction[R, T]): Expect[T] = map { r =>
    pf.applyOrElse(r, (r: R) => throw new NoSuchElementException(s"Expect.collect partial function is not defined at: $r"))
  }
  /**
    * @group Transformations
    */
  def zip[T](that: Expect[T]): Expect[(R, T)] = flatMap { r1 => that.map(r2 => (r1, r2)) }
  /**
    * @group Transformations
    */
  def zipWith[T, U](that: Expect[T])(f: (R, T) => U): Expect[U] = flatMap(r1 => that.map(r2 => f(r1, r2)))
  
  override def toString: String =
    s"""Expect:
       |\tHashCode: ${hashCode()}
       |\tCommand: $command
       |\tDefaultValue: $defaultValue
       |${expectBlocks.mkString("\n").indent()}
     """.stripMargin

  /**
    * Returns whether `other` is an Expect with the same `command`, the same `defaultValue`, the same `settings` and
    * the same `expects` as this `Expect`.
    *
    * In the cases that `expects` contains an Action with a function, eg. Returning, this method will return false,
    * because equality is not defined for functions.
    *
    * The method `structurallyEqual` can be used to test that two expects contain the same structure.
    *
    * @param other the other Expect to compare this Expect to.
    */
  override def equals(other: Any): Boolean = other match {
    case that: Expect[R] =>
      command == that.command &&
      defaultValue == that.defaultValue &&
      settings == that.settings &&
      expectBlocks == that.expectBlocks
    case _ => false
  }

  /**
    * @define subtypes expect blocks
    * Returns whether the other $type has the same
    *  - command
    *  - defaultvalue
    *  - settings
    *  - number of $subtypes and that each pair of $subtypes is structurally equal as this $type.
    *
    * @param other the other $type to campare this $type to.
    */
  def structurallyEquals(other: Expect[R]): Boolean = {
    command == other.command &&
      defaultValue == other.defaultValue &&
      settings == other.settings &&
      expectBlocks.size == other.expectBlocks.size &&
      expectBlocks.zip(other.expectBlocks).forall{ case (a, b) => a.structurallyEquals(b) }
  }
  override def hashCode(): Int = {
    val state: Seq[Any] = Seq(command, defaultValue, settings, expectBlocks)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}