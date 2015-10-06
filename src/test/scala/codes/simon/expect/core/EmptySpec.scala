package codes.simon.expect.core

import java.io.{EOFException, IOException}

import org.scalatest.time.{Seconds, Span}
import org.scalatest.{Matchers, FlatSpec}
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.ExecutionContext.Implicits.global

class EmptySpec extends FlatSpec with Matchers with ScalaFutures {
  "An Expect without a command" should "throw IllegalArgumentException" in {
    intercept[IllegalArgumentException] {
      new Expect("", Option.empty[String])()
    }
  }

  "An Expect with a command not available in the system" should "throw IOException" in {
    intercept[IOException] {
      new Expect("a", Option.empty[String])().run()
    }
  }

  "An Expect without expect blocks" should "return the default value" in {
    val e = new Expect("who", Option.empty[String])()
    e.run().futureValue should be (Option.empty[String])
  }

  "An Expect with empty expect blocks" should "fail with EOFException" in {
    val e = new Expect("who", Option.empty[String])(
      new ExpectBlock(),
      new ExpectBlock()
    )
    val defaultPatience = PatienceConfig(
      timeout = Span(Configs.Timeout.toSeconds + 2, Seconds),
      interval = Span(Configs.Timeout.toSeconds, Seconds)
    )
    e.run().failed.futureValue(defaultPatience) shouldBe a [EOFException]
  }
}
