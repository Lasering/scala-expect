package work.martins.simon.expect.core

import java.io.IOException

import org.scalatest.time.{Seconds, Span}
import org.scalatest.{Matchers, FlatSpec}
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.ExecutionContext.Implicits.global

class EmptySpec extends FlatSpec with Matchers with ScalaFutures {
  "An Expect without a command" should "throw IllegalArgumentException" in {
    intercept[IllegalArgumentException] {
      new Expect("", defaultValue = ())()
    }
  }

  "An Expect with a command not available in the system" should "throw IOException" in {
    intercept[IOException] {
      new Expect("ã", defaultValue = ())().run()
    }
  }

  "An Expect without expect blocks" should "return the default value" in {
    val e = new Expect("ls", defaultValue = ())()
    e.run().futureValue shouldBe Unit
  }

  "An Expect with an empty expect block" should "fail with IllegalArgumentException" in {
    intercept[IllegalArgumentException] {
      new Expect("ls", defaultValue = ())(new ExpectBlock())
    }
  }

  "An Expect with an empty when" should "return the default value" in {
    val e = new Expect("echo ola", defaultValue = ())(
      new ExpectBlock(
        new StringWhen("ola")()
      )
    )
    e.run().futureValue(PatienceConfig(
      timeout = Span(e.settings.timeout.toSeconds + 2, Seconds)
    )) shouldBe Unit
  }
}
