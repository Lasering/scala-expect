package work.martins.simon.expect.fluent

import java.nio.charset.Charset

import com.typesafe.config.Config
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.{Settings, core}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
  * @define type Expect
  */
class Expect[R](val command: Seq[String], val defaultValue: R, val settings: Settings = new Settings())
  extends Runnable[R] with Expectable[R] {

  def this(command: Seq[String], defaultValue: R, config: Config) = {
    this(command, defaultValue, new Settings(config))
  }
  def this(command: String, defaultValue: R, settings: Settings) = {
    this(splitBySpaces(command), defaultValue, settings)
  }
  def this(command: String, defaultValue: R, config: Config) = {
    this(command, defaultValue, new Settings(config))
  }
  def this(command: String, defaultValue: R) = {
    this(command, defaultValue, new Settings())
  }

  require(command.nonEmpty, "Expect must have a command to run.")
  import settings._

  //We decided to set expectableParent to 'this' to make it obvious that this is the root of all Expectables.
  protected val expectableParent: Expectable[R] = this
  private var expectBlocks = Seq.empty[ExpectBlock[R]]
  override def expect: ExpectBlock[R] = {
    val block = new ExpectBlock(this)
    expectBlocks :+= block
    block
  }
  override def addExpectBlock(f: Expect[R] => Unit): Expect[R] = {
    f(this)
    this
  }


  /** Creates a new $type by applying a function to the returned result of this $type. */
  def map[T](f: R => T): Expect[T] = {
    val newExpect = new Expect(command, f(defaultValue), settings)
    newExpect.expectBlocks = expectBlocks.map(_.map(newExpect, f))
    newExpect
  }
  /** Creates a new $type by applying a function to the returned result of this $type, and returns the result
    * of the function as the new $type. */
  def flatMap[T](f: R => core.Expect[T]): Expect[T] = {
    val newExpect = new Expect(command, f(defaultValue).defaultValue, settings)
    newExpect.expectBlocks = expectBlocks.map(_.flatMap(newExpect, f))
    newExpect
  }
  /**
    * Transform this $type result using the following strategy:
    *  - if `mapPF` is defined for the result then the result is mapped using mapPF.
    *  - otherwise, if `flatMapPF` is defined for the result then the result is flatMapped using flatMapPF.
    *  - otherwise a NoSuchElementException is thrown where the result would be expected.
    *
    * This function is very useful when we need to map this $type for some values of its result type and flatMap
    * this $type for some other values of its result type.
    *
    * To ensure you don't get NoSuchElementException you should take special care in ensuring
    * domain(mapPF) ∪ domain(flatMapPF) == domain(R)
    *
    * @param mapPF the function that will be applied when a map is needed.
    * @param flatMapPF the function that will be applied when a flatMap is needed.
    * @tparam T the type of the returned $type.
    * @return a new $type whose result is either mapped or flatMapped according to whether mapPF or
    *         flatMapPF is defined for the given result.
    */
  def transform[T](mapPF: PartialFunction[R, T])(flatMapPF: PartialFunction[R, core.Expect[T]]): Expect[T] = {
    def notDefined(r: R): T = throw new NoSuchElementException(s"Expect.fullCollect neither mapPF nor flatMapPF are defined at $r (the Expect default value)")

    val newDefaultValue = mapPF.applyOrElse(defaultValue, { r: R =>
      flatMapPF.andThen(_.defaultValue).applyOrElse(r, notDefined)
    })

    val newExpect = new Expect[T](command, newDefaultValue, settings)
    newExpect.expectBlocks = expectBlocks.map(_.transform(newExpect)(mapPF)(flatMapPF))
    newExpect
  }


  /**
    * @return the core.Expect equivalent of this fluent.Expect.
    */
  def toCore: core.Expect[R] = new core.Expect[R](command, defaultValue, settings)(expectBlocks.map(_.toCore):_*)

  //We decided to set runnableParent to 'this' to make it obvious that this is the root of all Runnables.
  protected val runnableParent: Runnable[R] = this
  override def run(timeout: FiniteDuration = timeout, charset: Charset = charset,
                   bufferSize: Int = bufferSize, redirectStdErrToStdOut: Boolean = redirectStdErrToStdOut)
                  (implicit ex: ExecutionContext): Future[R] = {
    toCore.run(timeout, charset, bufferSize, redirectStdErrToStdOut)(ex)
  }

  override def toString: String =
    s"""Expect:
        |\tCommand: $command
        |\tDefaultValue: $defaultValue
        |${expectBlocks.mkString("\n").indent()}
     """.stripMargin
  override def equals(other: Any): Boolean = other match {
    case that: Expect[R] =>
        command == that.command &&
        defaultValue == that.defaultValue &&
        settings == that.settings &&
        expectBlocks == that.expectBlocks
    case _ => false
  }
  override def hashCode(): Int = {
    val state: Seq[Any] = Seq(command, defaultValue, settings, expectBlocks)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
