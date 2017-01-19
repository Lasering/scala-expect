package work.martins.simon.expect

import org.scalatest.{Assertion, AsyncTestSuite, Matchers}
import org.scalatest.concurrent.ScalaFutures
import work.martins.simon.expect.core.{Expect, ExpectBlock, When}

import scala.concurrent.Future

trait TestUtils extends ScalaFutures with Matchers { test: AsyncTestSuite =>
  val addedValue = "this is it"
  def appendToBuilder(builder: StringBuilder): Unit = builder.append(addedValue)

  def constructExpect[R](defaultValue: R, whens: When[R]*): Expect[R] = new Expect("ls", defaultValue)(ExpectBlock(whens:_*))
  def constructExpect(whens: When[String]*): Expect[String] = constructExpect("", whens:_*)

  def testActionsAndResult[R](expect: Expect[R], builder: StringBuilder, expectedResult: R, numberOfAppends: Int = 1): Future[Assertion] = {
    //Ensure the actions were not executed while constructing and transforming the expect
    builder.result() shouldBe empty
    expect.run() map { obtainedResult =>
      //Ensure the actions were executed
      builder.result() shouldBe (addedValue * numberOfAppends)
      obtainedResult shouldBe expectedResult
    }
  }

  def testActionsAndFailedResult[R](expect: Expect[R], builder: StringBuilder): Future[Assertion] = {
    //Ensure the actions were not executed while constructing and transforming the expect
    builder.result() shouldBe empty
    expect.run().failed map { obtainedResult =>
      //Ensure the actions were executed
      builder.result() shouldBe addedValue
      obtainedResult shouldBe a [NoSuchElementException]
    }
  }
}
