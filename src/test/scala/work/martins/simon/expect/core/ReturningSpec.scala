package work.martins.simon.expect.core

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class ReturningSpec extends FlatSpec with Matchers with ScalaFutures {
  def defaultPatience(e: Expect[_]) = PatienceConfig(
    timeout = Span(e.settings.timeout.toSeconds + 2, Seconds)
  )

  "An Expect" should "return the specified value" in {
    val e = new Expect("scala", defaultValue = "")(
      new ExpectBlock (
        new StringWhen("Scala version") (
          Returning(() => "ReturnedValue")
        )
      )
    )
    e.run().futureValue(defaultPatience(e)) shouldBe "ReturnedValue"
  }

  it should "only invoke the returning function when that returning action is executed" in {
    var test = 5
    val e = new Expect("scala", "")(
      new ExpectBlock (
        new StringWhen("Scala version") (
          Returning { () =>
            test = 7
            "ReturnedValue"
          }
        )
      )
    )
    test shouldBe 5
    whenReady(e.run()) { s =>
      test shouldBe 7
      s shouldBe "ReturnedValue"
    }(defaultPatience(e))
  }

  it should "only return the last returning action but still execute the other actions" in {
    var test = 5
    val e = new Expect("scala", defaultValue = "")(
      new ExpectBlock (
        new RegexWhen("""Scala version (\d+\.\d+\.\d+)""".r) (
          ReturningWithRegex{ m =>
            test = 6
            m.group(1)
          },
          Returning(() => "5")
        )
      )
    )
    test shouldBe 5
    whenReady(e.run()) { s =>
      test shouldBe 6
      s shouldBe "5"
    }(defaultPatience(e))
  }

  it should "not execute any action after an exit action" in {
    var test = 5
    val e = new Expect("scala", defaultValue = "")(
      new ExpectBlock(
        new RegexWhen("""Scala version (\d+\.\d+\.\d+)""".r) (
          ReturningWithRegex(_.group(1)),
          Exit,
          Returning { () =>
            test = 7
            "ThisValue"
          }
        )
      )
    )

    whenReady(e.run()) { s =>
      test shouldBe 5
      s should not be "ThisValue"
    }(defaultPatience(e))
  }

  it should "be able to interact with the spawned program" in {
    val e = new Expect("scala", defaultValue = 5)(
      new ExpectBlock(
        new StringWhen("scala>")(
          Send("1 + 2\n")
        )
      ),
      new ExpectBlock(
        new RegexWhen("""res0: Int = (\d+)""".r)(
          ReturningWithRegex(_.group(1).toInt)
        )
      )
    )
    e.run().futureValue(PatienceConfig(
      timeout = Span(e.settings.timeout.toSeconds * 10, Seconds),
      interval = Span(e.settings.timeout.toSeconds * 10, Seconds)
    )) shouldBe 3
  }

  //Test returning with expect
}
