package codes.simon.expect.fluent

import java.nio.charset.Charset

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration
import codes.simon.expect.core.{Expect => CExpect, Constants}

object EndOfFile
object Timeout


object Expect {
  def spawn[R](command: String) = new Expect[R](command)
}
class Expect[R](command: String) extends Runnable[R] with Expectable[R] {
  val expectableParent: Expectable[R] = this
  private var expects = Seq.empty[ExpectBlock[R]]
  override def expect: ExpectBlock[R] = {
    val block = new ExpectBlock(this)
    expects :+= block
    block
  }

  val runnableParent: Runnable[R] = this
  override def run(timeout: FiniteDuration = Constants.TIMEOUT, charset: Charset = Constants.CHARSET, bufferSize: Int = Constants.BUFFER_SIZE)
                  (implicit ex: ExecutionContext): Future[Option[R]] = {
    new CExpect[R](command, expects.map(_.toCoreExpectBlock)).run(timeout, charset, bufferSize)(ex)
  }
}