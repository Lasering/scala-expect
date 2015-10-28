package codes.simon.expect.core

import java.io.{EOFException, IOException}

import org.scalatest.time.{Seconds, Span}
import org.scalatest.{Matchers, FlatSpec}
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.ExecutionContext.Implicits.global

class EmptySpec extends FlatSpec with Matchers with ScalaFutures {
  "An Expect without a command" should "throw IllegalArgumentException" in {
    intercept[IllegalArgumentException] {
      new Expect("")()
    }
  }

  "An Expect with a command not available in the system" should "throw IOException" in {
    intercept[IOException] {
      new Expect("a")().run()
    }
  }

  "An Expect without a defaultValue and expect blocks" should "return Unit" in {
    val e = new Expect("ls")()
    e.run().futureValue shouldBe Unit
  }

  "An Expect without expect blocks" should "return the default value" in {
    val e = new Expect("ls", Option.empty[String])()
    e.run().futureValue shouldBe Option.empty[String]
  }

  "An Expect with an empty expect block" should "fail with IllegalArgumentException" in {
    intercept[IllegalArgumentException] {
      new Expect("ls")(new ExpectBlock())
    }
  }
}
