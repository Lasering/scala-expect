package work.martins.simon.expect.core

import java.io.IOException

import org.scalatest.{FlatSpec, Matchers}
import work.martins.simon.expect.TestUtils

import scala.concurrent.ExecutionContext.Implicits.global

class EmptySpec extends FlatSpec with Matchers with TestUtils {
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
    new Expect("ls", defaultValue = ())().futureValue.shouldBe(())
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
    e.futureValue.shouldBe(())
  }
}
