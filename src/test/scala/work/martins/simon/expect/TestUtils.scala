package work.martins.simon.expect

import org.scalatest.Matchers

import scala.concurrent.duration.DurationInt
import org.scalatest.concurrent.ScalaFutures
import work.martins.simon.expect.core.{Expect, ExpectBlock, When}

import scala.concurrent.ExecutionContext.Implicits.global

trait TestUtils extends ScalaFutures with Matchers { test =>
  implicit class RichExpect[T](expect: Expect[T]) {
    val patienceConfig = PatienceConfig(
      timeout = scaled(expect.settings.timeout + 1.second),
      interval = scaled(500.millis)
    )

    def failedFutureValue: Throwable = expect.run().failed.futureValue(patienceConfig)
    def futureValue: T = expect.run().futureValue(patienceConfig)
    def whenReady[U](f: T => U): U = test.whenReady(expect.run())(f)(patienceConfig)
    def whenReadyFailed[U](f: Throwable => U): U = test.whenReady(expect.run().failed)(f)(patienceConfig)
  }

  val addedValue = "this is it"
  def appendToBuilder(builder: StringBuilder): Unit = builder.append(addedValue)

  def constructExpect[R](defaultValue: R, whens: When[R]*): Expect[R] = new Expect("ls", defaultValue)(ExpectBlock(whens:_*))
  def constructExpect(whens: When[String]*): Expect[String] = constructExpect("", whens:_*)

  def testActionsAndResult[R](expect: Expect[R], builder: StringBuilder, expectedResult: R, numberOfAppends: Int = 1): Unit = {
    //Ensure the actions were not executed in the mapping
    builder.result() shouldBe empty
    expect.whenReady { obtainedResult =>
      //Ensure the actions were executed
      builder.result() shouldBe (addedValue * numberOfAppends)
      obtainedResult shouldBe expectedResult
    }
  }

  def testActionsAndFailedResult[R](expect: Expect[R], builder: StringBuilder): Unit = {
    //Ensure the actions were not executed in the mapping
    builder.result() shouldBe empty
    expect.whenReadyFailed { obtainedResult =>
      //Ensure the actions were executed
      builder.result() shouldBe addedValue
      obtainedResult shouldBe a [NoSuchElementException]
    }
  }
}
