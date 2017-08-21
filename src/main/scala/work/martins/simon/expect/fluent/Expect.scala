package work.martins.simon.expect.fluent

import com.typesafe.config.Config
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.{FromInputStream, Settings, StdOut, core}

/**
  * @define type Expect
  */
class Expect[R](val command: Seq[String], val defaultValue: R, val settings: Settings = new Settings()) extends Expectable[R] {
  def this(command: Seq[String], defaultValue: R, config: Config) = {
    this(command, defaultValue, Settings.fromConfig(config))
  }
  def this(command: String, defaultValue: R, settings: Settings) = {
    this(splitBySpaces(command), defaultValue, settings)
  }
  def this(command: String, defaultValue: R, config: Config) = {
    this(command, defaultValue, Settings.fromConfig(config))
  }
  def this(command: String, defaultValue: R) = {
    this(command, defaultValue, new Settings())
  }

  require(command.nonEmpty, "Expect must have a command to run.")

  protected val expectableParent: Expect[R] = this

  protected var expectBlocks = Seq.empty[ExpectBlock[R]]
  protected def newExpectBlock(block: ExpectBlock[R]): ExpectBlock[R] = {
    expectBlocks :+= block
    block
  }

  override def expect: ExpectBlock[R] = newExpectBlock(new ExpectBlock(this, StdOut))
  override def expect(from: FromInputStream): ExpectBlock[R] = newExpectBlock(new ExpectBlock(this, from))
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
