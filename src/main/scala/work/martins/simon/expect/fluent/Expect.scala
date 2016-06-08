package work.martins.simon.expect.fluent

import com.typesafe.config.Config
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.{Settings, core}

/**
  * @define type Expect
  */
class Expect[R](val command: Seq[String], val defaultValue: R, val settings: Settings = new Settings()) extends Expectable[R] {
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

  /**
    * @return the core.Expect equivalent of this fluent.Expect.
    */
  def toCore: core.Expect[R] = new core.Expect[R](command, defaultValue, settings)(expectBlocks.map(_.toCore):_*)

  override def toString: String =
    s"""Expect:
        |\tHashCode: ${hashCode()}
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
