package work.martins.simon.expect.fluent

import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.{Settings, core}

/**
  * @define type Expect
  */
case class Expect[R](command: Seq[String], defaultValue: R, settings: Settings = Settings.fromConfig()) extends Expectable[R] {
  def this(command: String, defaultValue: R, settings: Settings) = this(splitBySpaces(command), defaultValue, settings)
  def this(command: String, defaultValue: R) = this(command, defaultValue, Settings.fromConfig())

  require(command.nonEmpty, "Expect must have a command to run.")

  protected val expectableParent: Expect[R] = this
  protected var expectBlocks = Seq.empty[ExpectBlock[R]]
  override def expect: ExpectBlock[R] = {
    val block = ExpectBlock[R](this)
    expectBlocks :+= block
    block
  }

  override def addExpectBlock(f: Expect[R] => ExpectBlock[R]): Expect[R] = {
    f(this)
    this
  }

  /**
    * @return the core.Expect equivalent of this fluent.Expect.
    */
  def toCore: core.Expect[R] = new core.Expect[R](command, defaultValue, settings)(expectBlocks.map(_.toCore):_*)

  override def toString: String =
    s"""Expect:
        |\tCommand: $command
        |\tDefaultValue: $defaultValue
        |\tSettings: $settings
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
  override def hashCode(): Int = Seq(command, defaultValue, settings, expectBlocks).map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
}
