package work.martins.simon.expect.dsl

import java.nio.charset.Charset

import com.typesafe.config.Config
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect._
import work.martins.simon.expect.fluent._

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

  protected[dsl] val fluentExpect = new fluent.Expect(command, defaultValue, settings)
  private val stack = new mutable.Stack[DSL[R]]

  def build(currentDefinition: DSL[R], block: => Unit): Unit = {
    stack.push(currentDefinition)
    block
    stack.pop()
  }

  //This is the only entry point of the DSL
  def expect: ExpectBlockDefinition[R] = {
    require(stack.isEmpty, "Expect block must be the top level object.")
    new ExpectBlockDefinition(this, fluentExpect.expect)
  }

  private def addWhen[W <: When[R]](block: DSL[R] => WhenDefinition[R, W]): WhenDefinition[R, W] = {
    require(stack.size == 1/* && stack.top.isInstanceOf[ExpectDefinition[R]]*/, "When can only be added inside an Expect.")
    block(stack.top)
  }
  def when(pattern: String): WhenDefinition[R, StringWhen[R]] = addWhen(_.when(pattern))
  def when(pattern: Regex): WhenDefinition[R, RegexWhen[R]] = addWhen(_.when(pattern))
  def when(pattern: Timeout.type): WhenDefinition[R, TimeoutWhen[R]] = addWhen(_.when(pattern))
  def when(pattern: EndOfFile.type): WhenDefinition[R, EndOfFileWhen[R]] = addWhen(_.when(pattern))

  def withBlock(block: DSL[R] => Unit): DSL[R] = {
    block(stack.top)
    this
  }

  private def addAction(block: DSL[R] => DSL[R]): DSL[R] = {
    require(stack.size == 2/* && stack.top.isInstanceOf[WhenDefinition[R, _]]*/, "An Action can only be added inside a When.")
    block(stack.top)
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

  def run(timeout: FiniteDuration = timeout, charset: Charset = charset,
          bufferSize: Int = bufferSize, redirectStdErrToStdOut: Boolean = redirectStdErrToStdOut)
         (implicit ex: ExecutionContext): Future[R] = {
    fluentExpect.run(timeout, charset, bufferSize, redirectStdErrToStdOut)(ex)
  }

  override def toString: String = fluentExpect.toString

  def toCore: core.Expect[R] = fluentExpect.toCore
}
