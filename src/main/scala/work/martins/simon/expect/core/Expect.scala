package work.martins.simon.expect.core

import java.nio.charset.Charset

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.Settings
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core.RunContext.{ChangeToNewExpect, Continue, Terminate}

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/**
  * Expect allows you to invoke CLIs and ensure they return what you expect.
  * You can describe a cli interaction which will be executed when `run` is invoked.
  *
  * Each interaction is described inside an `ExpectBlock` using `When`s. Each `When` states under which
  * circumstances the corresponding actions are to be executed. For example:
  *
  * {{{
  * import work.martins.simon.core._
  * import scala.concurrent.ExecutionContext.Implicits.global
  *
  * val e = new Expect("bc -i", defaultValue = 5)(
  *   ExpectBlock(
  *     StringWhen("For details type `warranty'.")(
  *       Sendln("1 + 2")
  *     )
  *   ),
  *   ExpectBlock(
  *     RegexWhen("""\n(\d+)\n""".r)(
  *       SendlnWithRegex { m =>
  *         val previousAnswer = m.group(1)
  *         println(s"Got $$previousAnswer")
  *         s"$$previousAnswer + 3"
  *       }
  *     )
  *   ),
  *   ExpectBlock(
  *     RegexWhen("""\n(\d+)\n""".r)(
  *       ReturningWithRegex(_.group(1).toInt)
  *     )
  *   )
  * )
  * e.run() //Returns 6 inside a Future[Int]
  * }}}
  *
  * We are executing the command `bc -i` which is a cli calculator.
  * `bc`, when it starts, writes to the terminal (more precisely to the standard out):
  *   "For details type `warranty'."
  * The expect we created reacts to it and sends to the terminal (more precisely to the standard in) the string "1 + 2".
  * However it only does so when the expect in ran, until then it does nothing, or in other words,
  * it only saves that it has to send "1 + 2" when the string "For details type `warranty'." appears on the StdOut.
  *
  * If this interaction is successful it then proceeds to the next one defined by the next `ExpectBlock`. This
  * interaction states that when `bc` outputs text that matches the regex """\n(\d+)\n""" the expect should react by
  * sending the string "3 + 3" where the first 3 is obtained by inspecting the regex match and extracting the first group.
  *
  * Finally the last interaction is very similar to the second one, with the only difference being the matched digit is
  * not used to send more text to `bc` but rather to set the value the expect will return in the end of the execution.
  *
  * If you find the syntax used to create the $type too verbose you should look at [[work.martins.simon.expect.fluent.Expect]]
  * or [[work.martins.simon.expect.dsl.Expect]] which provide a more terse syntax, especially `dsl.Expect` which provides
  * a syntax very similar to Tcl Expect.
  *
  * @param command the command this $type will execute.
  * @param defaultValue the value that will be returned if no `Returning` action is executed.
  * @param settings the settings that parameterize the execution of this $type.
  * @param expectBlocks the `ExpectBlock`s that describe the interactions this $type will execute.
  * @tparam R the type this $type returns.
  *
  * @define type `Expect`
  */
final class Expect[+R](val command: Seq[String], val defaultValue: R, val settings: Settings = new Settings())
                     (val expectBlocks: ExpectBlock[R]*) extends LazyLogging {
  def this(command: Seq[String], defaultValue: R, config: Config)(expects: ExpectBlock[R]*) = {
    this(command, defaultValue, Settings.fromConfig(config))(expects: _*)
  }
  def this(command: String, defaultValue: R, settings: Settings)(expectBlocks: ExpectBlock[R]*) = {
    this(splitBySpaces(command), defaultValue, settings)(expectBlocks: _*)
  }
  def this(command: String, defaultValue: R, config: Config)(expectBlocks: ExpectBlock[R]*) = {
    this(command, defaultValue, Settings.fromConfig(config))(expectBlocks: _*)
  }
  def this(command: String, defaultValue: R)(expectBlocks: ExpectBlock[R]*) = {
    this(command, defaultValue, new Settings())(expectBlocks: _*)
  }

  require(command.nonEmpty, "Expect must have a command to run.")

  def run(timeout: FiniteDuration = settings.timeout, charset: Charset = settings.charset)
         (implicit ex: ExecutionContext): Future[R] = {
    run(new Settings(timeout, charset))
  }
  def run(settings: Settings)(implicit ex: ExecutionContext): Future[R] = {
    run(NuProcessRichProcess(command, settings))
  }
  def run(process: RichProcess)(implicit ex: ExecutionContext): Future[R] = {
    val runContext = RunContext(process, value = defaultValue, executionAction = Continue)
    
    logger.info(runContext.withId("Running command: " + command.mkString("\"", " ", "\"")))
    logger.debug(runContext.withId(runContext.settings.toString))

    def success(runContext: RunContext[R]): Future[R] = Future {
      runContext.process.destroy()
    } map { _ =>
      logger.info(runContext.withId(s"Finished returning: ${runContext.value}"))
      runContext.value
    }

    def innerRun(expectBlocks: Seq[ExpectBlock[R]], runContext: RunContext[R]): Future[R] = {
      expectBlocks.headOption.map { headExpectBlock =>
        //We still have expect blocks to run
        val result = headExpectBlock.run(runContext).flatMap { innerRunContext =>
          innerRunContext.executionAction match {
            case Continue =>
              //Continue with the remaining expect blocks
              innerRun(expectBlocks.tail, innerRunContext)
            case Terminate =>
              success(innerRunContext)
            case ChangeToNewExpect(newExpect) =>
              innerRunContext.process.destroy()
              newExpect.asInstanceOf[Expect[R]].run(innerRunContext.process.withCommand(newExpect.command))
          }
        }
        //If we get an exception while running the head expect block we want to make sure the rich process is destroyed.
        result.transformWith {
          case Success(r) => Future.successful(r) // The success function already destroys the process
          case Failure(t) => Future { runContext.process.destroy() }.map(_ => throw t)
        }
      } getOrElse {
        //No more expect blocks. Just return the success value.
        success(runContext)
      }
    }

    innerRun(expectBlocks, runContext)
  }
  
  /** Creates a new $type by applying a function to the returned result of this $type.
    *
    * @tparam T the type of the returned $type
    * @param f the function which will be applied to the returned result of this $type
    * @return an $type which will return the result of the application of the function `f`
    * @group Transformations
    */
  def map[T](f: R => T): Expect[T] = {
    new Expect(command, f(defaultValue), settings)(expectBlocks.map(_.map(f)):_*)
  }
  /** Creates a new $type by applying a function to the returned result of this $type, and returns the result
    * of the function as the new $type.
    *
    * @tparam T the type of the returned $type
    * @param f the function which will be applied to the returned result of this $type
    * @return the $type returned as the result of the application of the function `f`
    * @group Transformations
    */
  def flatMap[T](f: R => Expect[T]): Expect[T] = {
    new Expect(command, f(defaultValue).defaultValue, settings)(expectBlocks.map(_.flatMap(f)):_*)
  }
  /** Transform this $type result using the following strategy:
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
    *   countFilesInFolder(folder).transform({
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
    *   }, {
    *     case Left(l) => Left(l)
    *     case Right(numberOfFiles) if numberOfFiles == 0 => Right(())
    *   })
    * }
    * }}}
    * @tparam T the type of the returned $type
    * @param flatMapPF the function that will be applied when a flatMap is needed
    * @param mapPF the function that will be applied when a map is needed
    * @return a new $type whose result is either flatMapped or mapped according to whether flatMapPF or
    *         mapPF is defined for the given result
    * @group Transformations
    */
  def transform[T](flatMapPF: PartialFunction[R, Expect[T]], mapPF: PartialFunction[R, T]): Expect[T] = {
    def notDefined(r: R): T = throw new NoSuchElementException(s"Expect.transform neither flatMapPF nor mapPF are defined at $r (the Expect default value)")

    val newDefaultValue = flatMapPF.andThen(_.defaultValue).applyOrElse(defaultValue, { r: R =>
      mapPF.applyOrElse(r, notDefined)
    })

    new Expect[T](command, newDefaultValue, settings)(expectBlocks.map(_.transform(flatMapPF, mapPF)):_*)
  }
  /** Creates a new $type with one level of nesting flattened, this method is equivalent to `flatMap(identity)`.
    * @tparam T the type of the returned $type
    * @return an $type with one level of nesting flattened
    * @group Transformations
    */
  def flatten[T](implicit ev: R <:< Expect[T]): Expect[T] = flatMap(ev)
  /** Creates a new $type by filtering its result with a predicate.
    *
    *  If the current $type result satisfies the predicate, the new $type will also hold that result.
    *  Otherwise, the resulting $type will fail with a `NoSuchElementException`.
    *
    * @param p the predicate to apply to the result of this $type
    * @group Transformations
    */
  def filter(p: R => Boolean): Expect[R] = map { r =>
    if (p(r)) r else throw new NoSuchElementException(s"Expect.filter predicate is not satisfied for: $r.")
  }
  /** Used by for-comprehensions.
    * @group Transformations
    */
  def withFilter(p: R => Boolean): Expect[R] = filter(p)
  /** Creates a new $type by mapping the result of the current $type, if the given partial function is defined at that value.
    *
    *  If the current $type contains a value for which the partial function is defined, the new $type will also hold that value.
    *  Otherwise, the resulting $type will fail with a `NoSuchElementException`.
    *
    * @tparam T the type of the returned $type
    * @param pf the `PartialFunction` to apply to the result of this $type
    * @return an $type holding the result of application of the `PartialFunction` or a `NoSuchElementException`
    * @group Transformations
    */
  def collect[T](pf: PartialFunction[R, T]): Expect[T] = map { r =>
    pf.applyOrElse(r, (r: R) => throw new NoSuchElementException(s"Expect.collect partial function is not defined at: $r"))
  }
  /** Zips the results of `this` and `that` $type, and creates
    *  a new $type holding the tuple of their results.
    *
    * @tparam T the type of the returned $type
    * @param that the other $type
    * @return an $type with the results of both ${type}s
    * @group Transformations
    */
  def zip[T](that: Expect[T]): Expect[(R, T)] = zipWith(that)((r1, r2) => (r1, r2))
  /** Zips the results of `this` and `that` $type using a function `f`,
    *  and creates a new $type holding the result.
    *
    * @tparam T the type of the other $type
    * @tparam U the type of the resulting $type
    * @param that the other $type
    * @param f the function to apply to the results of `this` and `that`
    * @return an $type with the result of the application of `f` to the results of `this` and `that`
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
    * Returns whether `other` is an $type with the same `command`, the same `defaultValue`, the same `settings` and
    * the same `expects` as this $type.
    *
    * In the cases that `expects` contains an Action with a function, eg. Returning, this method will return false,
    * because equality is not defined for functions.
    *
    * The method `structurallyEqual` can be used to test that two expects contain the same structure.
    *
    * @param other the other $type to compare this $type to.
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
    * @param other the other $type to compare this $type to.
    */
  def structurallyEquals[RR >: R](other: Expect[RR]): Boolean = {
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