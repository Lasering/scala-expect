package work.martins.simon.expect.core

import java.io.{EOFException, IOException}

import org.scalatest.time.{Seconds, Span}
import org.scalatest.{Matchers, FlatSpec}
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.ExecutionContext.Implicits.global

class EmptySpec extends FlatSpec with Matchers with ScalaFutures {
  "An Expect without a command" should "throw IllegalArgumentException" in {
    intercept[IllegalArgumentException] {
      new Expect("", defaultValue = Unit)()
    }
  }

  "An Expect with a command not available in the system" should "throw IOException" in {
    intercept[IOException] {
      new Expect("ã", defaultValue = Unit)().run()
    }
  }

  "An Expect without expect blocks" should "return the default value" in {
    val e = new Expect("ls", defaultValue = Unit)()
    e.run().futureValue shouldBe Unit
  }

  "An Expect with an empty expect block" should "fail with IllegalArgumentException" in {
    intercept[IllegalArgumentException] {
      new Expect("ls", defaultValue = Unit)(new ExpectBlock())
    }
  }
}
