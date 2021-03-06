package work.martins.simon.expect.dsl

import scala.util.matching.Regex
import scala.util.matching.Regex.Match
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect._

case class Expect[R](command: Seq[String], defaultValue: R, settings: Settings = Settings.fromConfig()) {
  def this(command: String, defaultValue: R, settings: Settings) = {
    this(splitBySpaces(command), defaultValue, settings)
  }
  def this(command: String, defaultValue: R) = {
    this(command, defaultValue, new Settings())
  }
  require(command.nonEmpty, "Expect must have a command to run.")

  private[expect] val fluentExpect = fluent.Expect(command, defaultValue, settings)

  protected var expectBlock: Option[fluent.ExpectBlock[R]] = None
  protected var when: Option[fluent.When[R]] = None
  
  private def newExpect(block: fluent.Expect[R] => fluent.ExpectBlock[R])(f: => Unit): Unit = {
    require(expectBlock.isEmpty && when.isEmpty, "Expect block must be the top level object.")
    val createdBlock = block(fluentExpect)
    expectBlock = Some(createdBlock)
    f
    expectBlock = None
    require(createdBlock.containsWhens(), "Expect block cannot be empty.")
  }
  def expect(f: => Unit): Unit = newExpect(_.expect)(f)
  def addExpectBlock(block: Expect[R] => Unit): Unit = block(this)

  private def newWhen[W <: fluent.When[R]](block: fluent.ExpectBlock[R] => W)(f: => Unit): Unit = {
    require(expectBlock.isDefined && when.isEmpty, "When can only be added inside an Expect Block.")
    expectBlock.foreach { eb =>
      when = Some(block(eb))
      f
      when = None
    }
  }
  def when(pattern: String)(f: => Unit): Unit = newWhen(_.when(pattern))(f)
  def when(pattern: String, readFrom: FromInputStream)(f: => Unit): Unit = newWhen(_.when(pattern, readFrom))(f)
  def when(pattern: Regex)(f: => Unit): Unit = newWhen(_.when(pattern))(f)
  def when(pattern: Regex, readFrom: FromInputStream)(f: => Unit): Unit = newWhen(_.when(pattern, readFrom))(f)
  def when(pattern: EndOfFile.type)(f: => Unit): Unit = newWhen(_.when(pattern))(f)
  def when(pattern: EndOfFile.type, readFrom: FromInputStream)(f: => Unit): Unit = newWhen(_.when(pattern, readFrom))(f)
  def when(pattern: Timeout.type)(f: => Unit): Unit = newWhen(_.when(pattern))(f)
  def addWhen(block: Expect[R] => Unit): Unit = block(this)
  def addWhens(block: Expect[R] => Unit): Unit = block(this)

  private def newAction(block: fluent.When[R] => fluent.When[R]): Unit = {
    require(expectBlock.isDefined && when.isDefined, "An Action can only be added inside a When.")
    when.foreach { w =>
      block(w)
      ()
    }
  }
  private def newRegexAction(block: fluent.RegexWhen[R] => fluent.RegexWhen[R]): Unit = {
    val regexWhen: Option[fluent.RegexWhen[R]] = when.collect { case r: fluent.RegexWhen[R] => r }
    require(expectBlock.isDefined && when.isDefined, "An Action can only be added inside a When.")
    require(regexWhen.isDefined, "This action can only be invoked for RegexWhen")
    regexWhen.foreach { w =>
      block(w)
      ()
    }
  }
  def send(text: String, sensitive: Boolean = false): Unit = newAction(_.send(text, sensitive))
  def send(text: Match => String): Unit = newRegexAction(_.send(text))
  def sendln(text: String, sensitive: Boolean = false): Unit = newAction(_.sendln(text, sensitive))
  def sendln(text: Match => String): Unit = newRegexAction(_.sendln(text))
  def returning(result: => R): Unit = newAction(_.returning(result))
  def returning(result: Match => R): Unit = newRegexAction(_.returning(result))
  def returningExpect(result: => core.Expect[R]): Unit = newAction(_.returningExpect(result))
  def returningExpect(result: Match => core.Expect[R]): Unit = newRegexAction(_.returningExpect(result))
  def exit(): Unit = newAction(_.exit())
  def addActions(block: Expect[R] => Unit): Unit = block(this)

  def toCore: core.Expect[R] = fluentExpect.toCore

  override def toString: String = fluentExpect.toString
  override def equals(other: Any): Boolean = other match {
    case that: Expect[R] => fluentExpect.equals(that.fluentExpect)
    case _ => false
  }
  override def hashCode(): Int = fluentExpect.hashCode()
}
