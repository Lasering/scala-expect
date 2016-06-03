package work.martins.simon.expect.core

import org.scalatest.{FlatSpec, Matchers}
import work.martins.simon.expect.TestUtils
import work.martins.simon.expect.core.actions._

class ReturningSpec extends FlatSpec with Matchers with TestUtils {
  "An Expect" should "return the specified value" in {
    val e = new Expect("bc -i", defaultValue = "")(
      ExpectBlock (
        StringWhen("bc") (
          Returning("ReturnedValue")
        )
      )
    )
    e.futureValue shouldBe "ReturnedValue"
  }

  it should "only invoke the returning function when the corresponding When is executed" in {
    var test = 5
    val e = new Expect("bc -i", defaultValue = "")(
      ExpectBlock (
        StringWhen("bc") (
          Returning {
            test += 1
            "ReturnedValue"
          }
        )
      )
    )
    test shouldBe 5
    e.whenReady { s =>
      test shouldBe 6
      s shouldBe "ReturnedValue"
    }
  }

  it should "only return the last returning action but still execute the other actions" in {
    var test = 5
    val e = new Expect("bc -i", defaultValue = "")(
      ExpectBlock (
        RegexWhen("""bc (\d+\.\d+\.\d+)""".r) (
          ReturningWithRegex{ m =>
            test += 1
            m.group(1)
          },
          Returning{
            test += 1
            "5"
          }
        )
      )
    )

    test shouldBe 5
    e.whenReady { s =>
      test shouldBe 7
      s shouldBe "5"
    }
  }

  it should "not execute any action after an exit action" in {
    var test = 5
    val e = new Expect("bc -i", defaultValue = "")(
      ExpectBlock(
        RegexWhen("""bc (\d+\.\d+\.\d+)""".r) (
          ReturningWithRegex(_.group(1)),
          Exit(),
          Returning {
            test += 1
            "ThisValue"
          }
        )
      )
    )

    test shouldBe 5

    e.whenReady { s =>
      test shouldBe 5
      s should not be "ThisValue"
    }
  }

  it should "be able to interact with the spawned program" in {
    val e = new Expect("bc -i", defaultValue = 5)(
      ExpectBlock(
        StringWhen("For details type `warranty'.")(
          Sendln("1 + 2")
        )
      ),
      ExpectBlock(
        RegexWhen("""\n(\d+)\n""".r)(
          SendlnWithRegex { m =>
            val previousAnswer = m.group(1)
            s"$previousAnswer + 3"
          }
        )
      ),
      ExpectBlock(
        RegexWhen("""\n(\d+)\n""".r)(
          ReturningWithRegex(_.group(1).toInt)
        )
      )
    )
    e.futureValue shouldBe 6
  }

  it should "fail if an exception is thrown inside an action" in {
    val e = new Expect("bc -i", defaultValue = 5)(
      ExpectBlock(
        StringWhen("For details type `warranty'.")(
          Sendln("1 + 2"),
          Returning { (u: Unit) =>
            throw new IllegalArgumentException()
          }
        )
      )
    )
    e.failedFutureValue shouldBe a [IllegalArgumentException]
  }

  //Test returning with expect
}
