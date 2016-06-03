package work.martins.simon.expect.core

import java.nio.charset.Charset

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.Settings
import work.martins.simon.expect.StringUtils._

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

/**
  *@define type Expect
  */
class Expect[R](val command: Seq[String], val defaultValue: R, val settings: Settings = new Settings())
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

  import settings._

  def run(timeout: FiniteDuration = timeout, charset: Charset = charset,
          bufferSize: Int = bufferSize, redirectStdErrToStdOut: Boolean = redirectStdErrToStdOut)
         (implicit ex: ExecutionContext): Future[R] = {
    val richProcess = RichProcess(command, timeout, charset, bufferSize, redirectStdErrToStdOut)
    val expectID = s"[ID:${hashCode()}]"
    logger.info(s"""$expectID Launched: "${command.mkString(" ")}"""")

    def successful(intermediateResult: IntermediateResult[R]): Future[R] = {
      logger.info(s"$expectID Finished returning: ${intermediateResult.value}")
      richProcess.destroy()
      Future.successful(intermediateResult.value)
    }

    def innerRun(intermediateResult: IntermediateResult[R], expectBlocks: Seq[ExpectBlock[R]]): Future[R] = {
      expectBlocks.headOption.map { headExpectBlock =>
        //We still have expect blocks to run
        val result = headExpectBlock.run(richProcess, intermediateResult, expectID).flatMap { innerResult =>
          innerResult.executionAction match {
            case Continue =>
              //Continue with the remaining expect blocks
              innerRun(innerResult, expectBlocks.tail)
            case Terminate =>
              //Simply terminate with the innerResult
              successful(innerResult)
            case ChangeToNewExpect(newExpect) =>
              richProcess.destroy()
              newExpect.asInstanceOf[Expect[R]].run(richProcess.timeout, richProcess.charset, richProcess.bufferSize)
          }
        }
        //If we get an exception while running the head expect block we want to make sure the rich process is destroyed.
        result onFailure { case _ => richProcess.destroy() }
        result
      } getOrElse {
        //No more expect blocks. We just return the current intermediateResult
        successful(intermediateResult)
      }
    }

    innerRun(IntermediateResult(output = "", defaultValue, Continue), expectBlocks)
  }

  /** Creates a new $type by applying a function to the returned result of this $type. */
  def map[T](f: R => T): Expect[T] = {
    new Expect(command, f(defaultValue), settings)(expectBlocks.map(_.map(f)):_*)
  }
  /** Creates a new $type by applying a function to the returned result of this $type, and returns the result
    * of the function as the new $type. */
  def flatMap[T](f: R => Expect[T]): Expect[T] = {
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
    * To ensure you don't get NoSuchElementException you should take special care in ensuring
    * domain(flatMapPF) ∪ domain(mapPF) == domain(R)
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
    */
  def transform[T](flatMapPF: PartialFunction[R, Expect[T]])(mapPF: PartialFunction[R, T]): Expect[T] = {
    def notDefined(r: R): T = throw new NoSuchElementException(s"Expect.transform neither flatMapPF nor mapPF are defined at $r (the Expect default value)")

    val newDefaultValue = flatMapPF.andThen(_.defaultValue).applyOrElse(defaultValue, { r: R =>
      mapPF.applyOrElse(r, notDefined)
    })

    new Expect[T](command, newDefaultValue, settings)(expectBlocks.map(_.transform(flatMapPF)(mapPF)):_*)
  }

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
    * @param other the other Expect to campare this Expect to.
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
    * @define type expect
    * @define subtypes expect blocks
    * Returns whether the other $type has the same
    *  - command
    *  - defaultvalue
    *  - settings
    *  - number of $subtypes and that each pair of $subtypes is structurally equal
    * as this $type.
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