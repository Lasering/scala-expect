package work.martins.simon.expect.dsl

import java.nio.charset.Charset

import scala.collection.mutable
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

import work.martins.simon.expect.core.{Configs, EndOfFile, Timeout}
import work.martins.simon.expect.fluent

class Expect[R: ClassTag](val command: Seq[String], val defaultValue: R) extends DSL[R] {
  def this(command: String, defaultValue: R = Unit) = {
    this(command.split("""\s+""").filter(_.nonEmpty).toSeq, defaultValue)
  }
  val fluentExpect = new fluent.Expect(command, defaultValue)

  private val stack = new mutable.Stack[AbstractDefinition[R]]

  def build(currentDefinition: AbstractDefinition[R], block: => Unit): Unit = {
    stack.push(currentDefinition)
    block
    stack.pop()
  }

  //This is the only entry point of the DSL
  def expect: DSL[R] with Block[R] = {
    require(stack.isEmpty, "Expect block must be the top level object.")
    new ExpectDefinition(this, fluentExpect.expect)
  }

  def expect(pattern: String): DSLWithBlock = expect.apply(when(pattern))
  def expect(pattern: Regex): DSLWithBlock = expect.apply(when(pattern))
  def expect(pattern: Timeout.type): DSLWithBlock = expect.apply(when(pattern))
  def expect(pattern: EndOfFile.type): DSLWithBlock = expect.apply(when(pattern))

  private def addWhen(block: AbstractDefinition[R] => DSLWithBlock): DSLWithBlock = {
    require(stack.size == 1 && stack.top.isInstanceOf[ExpectDefinition[R]], "When can only be added inside an Expect.")
    block(stack.top)
  }
  def when(pattern: String): DSLWithBlock = addWhen(_.when(pattern))
  def when(pattern: Regex): DSLWithBlock = addWhen(_.when(pattern))
  def when(pattern: EndOfFile.type): DSLWithBlock = addWhen(_.when(pattern))
  def when(pattern: Timeout.type): DSLWithBlock = addWhen(_.when(pattern))

  def withBlock(block: DSL[R] => Unit): DSL[R] = {
    block(stack.top)
    this
  }

  private def addAction(block: AbstractDefinition[R] => DSL[R]): DSL[R] = {
    require(stack.size == 2 && stack.top.isInstanceOf[WhenDefinition[R, _]],
            "An Action can only be added inside a When.")
    block(stack.top)
  }
  def send(text: String): DSL[R] = addAction(_.send(text))
  def send(text: Match => String): DSL[R] = addAction(_.send(text))
  def sendln(text: String): DSL[R] = addAction(_.sendln(text))
  def sendln(text: Match => String): DSL[R] = addAction(_.sendln(text))
  def returning(result: => R): DSL[R] = addAction(_.returning(result))
  def returning(result: Match => R): DSL[R] = addAction(_.returning(result))
  def exit(): DSL[R] = addAction(_.exit())

  def run(timeout: FiniteDuration = Configs.timeout, charset: Charset = Configs.charset,
          bufferSize: Int = Configs.bufferSize, redirectStdErrToStdOut: Boolean = Configs.redirectStdErrToStdOut)
         (implicit ex: ExecutionContext): Future[R] = {
    fluentExpect.run(timeout, charset, bufferSize, redirectStdErrToStdOut)(ex)
  }

  override def toString: String = fluentExpect.toString
}
