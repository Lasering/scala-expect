package work.martins.simon.expect.fluent

import work.martins.simon.expect.StringUtils.*
import work.martins.simon.expect.{Settings, core}

//Useful conversion to use in returningExpect actions, which are waiting to receive a core.Expect
given fluentToCoreExpect[R]: Conversion[Expect[R], core.Expect[R]] = _.toCore

open case class Expect[R](command: Seq[String] | String, defaultValue: R, settings: Settings = Settings.fromConfig()) extends Expectable[R]:
  val commandSeq: Seq[String] = properCommand(command)
  require(commandSeq.nonEmpty, "Expect must have a command to run.")
  
  protected val expectableParent: Expect[R] = this
  protected var expectBlocks = Seq.empty[ExpectBlock[R]]
  override def expectBlock: ExpectBlock[R] =
    val block = ExpectBlock[R](this)
    expectBlocks :+= block
    block
  
  override def addExpectBlock(f: Expect[R] => ExpectBlock[R]): Expect[R] =
    f(this)
    this
  
  /** @return the core Expect equivalent of this Expect. */
  def toCore: core.Expect[R] = new core.Expect[R](command, defaultValue, settings)(expectBlocks.map(_.toCore)*)
  
  override def toString: String =
    s"""Expect:
        |\tCommand: $command
        |\tDefaultValue: $defaultValue
        |\tSettings: $settings
        |${expectBlocks.mkString("\n").indent()}""".stripMargin
  override def equals(other: Any): Boolean = other match
    case e @ Expect(`command`, `defaultValue`, `settings`) if e.expectBlocks == expectBlocks => true
    case _ => false
  override def hashCode(): Int = Seq(command, defaultValue, settings, expectBlocks).map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)