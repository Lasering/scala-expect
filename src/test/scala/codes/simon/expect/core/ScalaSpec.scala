package codes.simon.expect.core

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex.Match

class ScalaSpec extends FlatSpec with Matchers with ScalaFutures {
  val defaultPatience = PatienceConfig(
    timeout = Span(Configs.Timeout.toSeconds + 2, Seconds),
    interval = Span(Configs.Timeout.toSeconds, Seconds)
  )

  "An Expect " should "returned the specified value" in {
    val e = new Expect("scala", "")(
      new ExpectBlock (
        new RegexWhen("""Scala version (\d+\.\d+\.\d+)""".r) (
          ReturningWithRegex(_.group(1))
        )
      )
    )
    e.run().futureValue(defaultPatience) should be (util.Properties.versionNumberString)
  }

  it should "be able to interact with the spawned program" in {
    val e = new Expect("scala", 5)(
      new ExpectBlock(
        new StringWhen("scala>")(
          Send("1 + 2\n")
        )
      ),
      new ExpectBlock(
        new RegexWhen("""res\d+: Int = (\d+)""".r)(
          ReturningWithRegex(_.group(1).toInt)
        )
      )
    )
    e.run().futureValue(defaultPatience) shouldBe 3
  }

  it should "only return the last returning action" in {
    val e = new Expect("scala", "")(
      new ExpectBlock (
        new RegexWhen("""Scala version (\d+\.\d+\.\d+)""".r) (
          ReturningWithRegex(_.group(1)),
          Returning(() => "5")
        )
      )
    )
    e.run().futureValue(defaultPatience) should be ("5")
  }

  it should "only invoke the returning function when that returning action is executed" in {
    var test = 5
    val e = new Expect("scala", "")(
      new ExpectBlock (
        new RegexWhen("""Scala version (\d+\.\d+\.\d+)""".r) (
          ReturningWithRegex{ m =>
            test = 7
            m.group(1)
          }
        )
      )
    )
    test shouldBe 5
    whenReady(e.run()) { s =>
      s should be (util.Properties.versionNumberString)
      test shouldBe 7
    }(defaultPatience)
  }

  it should "not execute any action after a exit action" in {
    var test = 5
    val e = new Expect("scala", "")(
      new ExpectBlock (
        new RegexWhen("""Scala version (\d+\.\d+\.\d+)""".r) (
          ReturningWithRegex{ m =>
            m.group(1)
          },
          Exit,
          Returning { () =>
            test = 7
            "ThisValue"
          }
        )
      )
    )
    whenReady(e.run()) { s =>
      s should not be "ThisValue"
      test shouldBe 5
    }(defaultPatience)
  }
}
