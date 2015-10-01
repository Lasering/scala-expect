package codes.simon.expect.dsl

import java.nio.charset.Charset

import codes.simon.expect.core.{Constants, Timeout, EndOfFile}
import codes.simon.expect.fluent.{Expect => FExpect, ExpectBlock, When}

import scala.collection.mutable
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

class Expect[R: ClassTag](command: String, defaultValue: R) extends DSL[R] {
  val fluentExpect = new FExpect(command, defaultValue)

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
    require(stack.size == 1 && stack.top.isInstanceOf[ExpectDefinition[R]], "When can only be added inside an expect.")
    block(stack.top)
  }
  def when(pattern: String) = addWhen(_.when(pattern))
  def when(pattern: Regex) = addWhen(_.when(pattern))
  def when(pattern: EndOfFile.type) = addWhen(_.when(pattern))
  def when(pattern: Timeout.type) = addWhen(_.when(pattern))

  private def addAction(block: AbstractDefinition[R] => DSL[R]): DSL[R] = {
    require(stack.size == 2 && stack.top.isInstanceOf[WhenDefinition[R, _]], "An action can only be added inside a when.")
    block(stack.top)
  }
  def send(text: String) = addAction(_.send(text))
  def send(text: Match => String) = addAction(_.send(text))
  def sendln(text: String) = addAction(_.sendln(text))
  def sendln(text: Match => String) = addAction(_.sendln(text))
  def returning(result: => R) = addAction(_.returning(result))
  def returning(result: Match => R) = addAction(_.returning(result))
  def exit() = addAction(_.exit())

  def run(timeout: FiniteDuration = Constants.TIMEOUT, charset: Charset = Constants.CHARSET,
          bufferSize: Int = Constants.BUFFER_SIZE, redirectStdErrToStdOut: Boolean = Constants.REDIRECT_STDERR_TO_STDOUT)
         (implicit ex: ExecutionContext): Future[R] = {
    fluentExpect.run(timeout, charset, bufferSize)(ex)
  }

  override def toString: String = fluentExpect.toString
}
