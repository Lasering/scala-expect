package work.martins.simon.expect.core

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex.Match

class ReturningSpec extends FlatSpec with Matchers with ScalaFutures {
  def defaultPatience(e: Expect[_]): PatienceConfig = PatienceConfig(
    timeout = Span(e.settings.timeout.toSeconds + 2, Seconds)
  )

  "An Expect" should "return the specified value" in {
    val e = new Expect("bc -i", defaultValue = "")(
      new ExpectBlock (
        new StringWhen("bc") (
          Returning(() => "ReturnedValue")
        )
      )
    )
    e.run().futureValue(defaultPatience(e)) shouldBe "ReturnedValue"
  }

  it should "only invoke the returning function when that returning action is executed" in {
    var test = 5
    val e = new Expect("bc -i", defaultValue = "")(
      new ExpectBlock (
        new StringWhen("bc") (
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
    val e = new Expect("bc -i", defaultValue = "")(
      new ExpectBlock (
        new RegexWhen("""bc (\d+\.\d+\.\d+)""".r) (
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
    val e = new Expect("bc -i", defaultValue = "")(
      new ExpectBlock(
        new RegexWhen("""bc (\d+\.\d+\.\d+)""".r) (
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
    val e = new Expect("bc -i", defaultValue = 5)(
      new ExpectBlock(
        new StringWhen("For details type `warranty'.")(
          Sendln("1 + 2")
        )
      ),
      new ExpectBlock(
        new RegexWhen("""\n(\d+)\n""".r)(
          SendlnWithRegex { m: Match =>
            val previousAnswer = m.group(1)
            println(s"Got $previousAnswer")
            s"$previousAnswer + 3"
          }
        )
      ),
      new ExpectBlock(
        new RegexWhen("""\n(\d+)\n""".r)(
          ReturningWithRegex(_.group(1).toInt)
        )
      )
    )
    e.run().futureValue(defaultPatience(e)) shouldBe 6
  }

  it should "fail if an exception is thrown inside an action" in {
    val e = new Expect("bc -i", defaultValue = 5)(
      new ExpectBlock(
        new StringWhen("For details type `warranty'.")(
          Sendln("1 + 2"),
          Returning{ () =>
            throw new IllegalArgumentException()
          }
        )
      )
    )
    e.run().failed.futureValue(defaultPatience(e)) shouldBe a [IllegalArgumentException]
  }

  //Test returning with expect
}
