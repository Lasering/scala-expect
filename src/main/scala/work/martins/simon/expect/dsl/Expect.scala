package work.martins.simon.expect.dsl

import java.nio.charset.Charset

import com.typesafe.config.Config
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.{Settings, Timeout, EndOfFile, core, fluent}
import work.martins.simon.expect.fluent.{EndOfFileWhen, RegexWhen, StringWhen, TimeoutWhen, When => FWhen}

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

class Expect[R](val command: Seq[String], val defaultValue: R, val settings: Settings = new Settings()) extends DSL[R] {
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

  private val fluentExpect = new fluent.Expect(command, defaultValue, settings)
  private val stack = new mutable.Stack[DSL[R]]

  def build(currentDefinition: DSL[R], block: => Unit): Unit = {
    stack.push(currentDefinition)
    block
    stack.pop()
  }

  //This is the only entry point of the DSL
  def expect: ExpectBlock[R] = {
    require(stack.isEmpty, "Expect block must be the top level object.")
    new ExpectBlock(this, fluentExpect.expect)
  }
  def addExpectBlock(f: DSL[R] => ExpectBlock[R]): ExpectBlock[R] = f(this)

  private def newWhen[W <: FWhen[R]](block: ExpectBlock[R] => When[R, W]): When[R, W] = {
    require(stack.size == 1 && stack.top.isInstanceOf[ExpectBlock[R]], "When can only be added inside an Expect.")
    block(stack.top.asInstanceOf[ExpectBlock[R]])
  }
  def when(pattern: String): When[R, StringWhen[R]] = newWhen(_.when(pattern))
  def when(pattern: Regex): When[R, RegexWhen[R]] = newWhen(_.when(pattern))
  def when(pattern: Timeout.type): When[R, TimeoutWhen[R]] = newWhen(_.when(pattern))
  def when(pattern: EndOfFile.type): When[R, EndOfFileWhen[R]] = newWhen(_.when(pattern))
  def addWhen[W <: FWhen[R]](f: ExpectBlock[R] => When[R, W]): When[R, W] = newWhen(f)
  def addWhens(f: ExpectBlock[R] => DSL[R]): ExpectBlock[R] = {
    require(stack.size == 1 && stack.top.isInstanceOf[ExpectBlock[R]], "When can only be added inside an Expect.")
    val top = stack.top.asInstanceOf[ExpectBlock[R]]
    f(top)
    top
  }

  private def addAction(block: When[R, _] => DSL[R]): DSL[R] = {
    require(stack.size == 2 && stack.top.isInstanceOf[When[R, _]], "An Action can only be added inside a When.")
    block(stack.top.asInstanceOf[When[R, _]])
  }
  def send(text: String): DSL[R] = addAction(_.send(text))
  def send(text: Match => String): DSL[R] = addAction(_.send(text))
  def sendln(text: String): DSL[R] = addAction(_.sendln(text))
  def sendln(text: Match => String): DSL[R] = addAction(_.sendln(text))
  def returning(result: => R): DSL[R] = addAction(_.returning(result))
  def returning(result: Match => R): DSL[R] = addAction(_.returning(result))
  def returningExpect(result: => core.Expect[R]): DSL[R] = addAction(_.returningExpect(result))
  def returningExpect(result: Match => core.Expect[R]): DSL[R] = addAction(_.returningExpect(result))
  def exit(): DSL[R] = addAction(_.exit())
  def addActions(f: When[R, _] => DSL[R]): DSL[R] = addAction(f)

  def run(timeout: FiniteDuration = timeout, charset: Charset = charset,
          bufferSize: Int = bufferSize, redirectStdErrToStdOut: Boolean = redirectStdErrToStdOut)
         (implicit ex: ExecutionContext): Future[R] = {
    fluentExpect.run(timeout, charset, bufferSize, redirectStdErrToStdOut)(ex)
  }

  override def toString: String = fluentExpect.toString

  def toCore: core.Expect[R] = fluentExpect.toCore
  def toFluent: fluent.Expect[R] = fluentExpect
}