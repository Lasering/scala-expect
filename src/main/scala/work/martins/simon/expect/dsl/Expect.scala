package work.martins.simon.expect.dsl

import scala.util.NotGiven
import scala.util.matching.Regex
import scala.util.matching.Regex.Match
import work.martins.simon.expect.*
import work.martins.simon.expect.fluent.{Expect as _, *}

//Useful conversion to use in returningExpect actions, which are waiting to receive a core.Expect
given dslToCoreExpect[R]: Conversion[Expect[R], core.Expect[R]] = _.toCore

open case class Expect[R](command: Seq[String] | String, defaultValue: R, settings: Settings = Settings.fromConfig()) derives CanEqual:
  private val fluentExpect = fluent.Expect(command, defaultValue, settings)
  
  def expectBlock(whens: ExpectBlock[R] ?=> Unit)(using NotGiven[ExpectBlock[R]]): ExpectBlock[R] =
    // NotGiven prevents expectBlocks from being created inside expectBlocks
    given expectBlock: ExpectBlock[R] = fluentExpect.expectBlock
    whens
    require(expectBlock.containsWhens(), "Expect block cannot be empty.")
    expectBlock
  def addExpectBlock(f: Expect[R] => Unit): Unit = f(this)
  
  private def newWhen[W[X] <: When[X]](f: ExpectBlock[R] => W[R])(actions: W[R] ?=> Unit)(using expectBlock: ExpectBlock[R]): W[R] =
    given when: W[R] = f(expectBlock)
    actions
    when
  def when(pattern: String)(actions: StringWhen[R] ?=> Unit)(using ExpectBlock[R]): StringWhen[R] =
    newWhen(_.when(pattern))(actions)
  def when(pattern: String, readFrom: FromInputStream)(actions: StringWhen[R] ?=> Unit)(using ExpectBlock[R]): StringWhen[R] =
    newWhen(_.when(pattern, readFrom))(actions)
  def when(pattern: Regex)(actions: RegexWhen[R] ?=> Unit)(using ExpectBlock[R]): RegexWhen[R] =
    newWhen(_.when(pattern))(actions)
  def when(pattern: Regex, readFrom: FromInputStream)(actions: RegexWhen[R] ?=> Unit)(using ExpectBlock[R]): RegexWhen[R] =
    newWhen(_.when(pattern, readFrom))(actions)
  def when(pattern: EndOfFile.type)(actions: EndOfFileWhen[R] ?=> Unit)(using ExpectBlock[R]): EndOfFileWhen[R] =
    newWhen(_.when(pattern))(actions)
  def when(pattern: EndOfFile.type, readFrom: FromInputStream)(actions: EndOfFileWhen[R] ?=> Unit)(using ExpectBlock[R]): EndOfFileWhen[R] =
    newWhen(_.when(pattern, readFrom))(actions)
  def when(pattern: Timeout.type)(actions: TimeoutWhen[R] ?=> Unit)(using ExpectBlock[R]): TimeoutWhen[R] =
    newWhen(_.when(pattern))(actions)
  def addWhen(f: Expect[R] => Unit)(using ExpectBlock[R]): Unit = f(this)
  def addWhens(f: Expect[R] => Unit)(using ExpectBlock[R]): Unit = f(this)
  
  def send(text: String, sensitive: Boolean = false)(using when: When[R]): Unit = when.send(text, sensitive)
  def send(text: Match => String)(using when: RegexWhen[R]): Unit = when.send(text)
  def sendln(text: String, sensitive: Boolean = false)(using when: When[R]): Unit = when.sendln(text, sensitive)
  def sendln(text: Match => String)(using when: RegexWhen[R]): Unit = when.sendln(text)
  def returning(result: => R)(using when: When[R]): Unit = when.returning(result)
  def returning(result: Match => R)(using when: RegexWhen[R]): Unit = when.returning(result)
  def returningExpect(result: => core.Expect[R])(using when: When[R]): Unit = when.returningExpect(result)
  def returningExpect(result: Match => core.Expect[R])(using when: RegexWhen[R]): Unit = when.returningExpect(result)
  def exit()(using when: When[R]): Unit = when.exit()
  def addActions[W[X] <: When[X]](f: Expect[R] => Unit)(using W[R]): Unit = f(this)
  
  def toCore: core.Expect[R] = fluentExpect.toCore
  
  override def toString: String = fluentExpect.toString
  
  override def equals(other: Any): Boolean = other.asInstanceOf[Matchable] match
    case that: Expect[?] => fluentExpect.equals(that.fluentExpect)
    case _ => false
  override def hashCode(): Int = fluentExpect.hashCode()