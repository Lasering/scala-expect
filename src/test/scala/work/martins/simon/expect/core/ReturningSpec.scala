package work.martins.simon.expect.core

import org.scalatest.{AsyncFlatSpec, BeforeAndAfterEach}
import work.martins.simon.expect.TestUtils
import work.martins.simon.expect.core.actions._

class ReturningSpec extends AsyncFlatSpec with TestUtils with BeforeAndAfterEach {
  val builder = new StringBuilder("")
  val expectedValue = "ReturnedValue"

  override protected def beforeEach(): Unit = builder.clear()

  "An Expect" should "only return the last returning action before an exit but still execute the previous actions" in {
    //should "not execute any action after an exit action"
    val expect = constructExpect(StringWhen("LICENSE") (
      Returning {
        appendToBuilder(builder)
        "a"
      },
      Returning {
        appendToBuilder(builder)
        "b"
      },
      Exit(),
      Returning {
        appendToBuilder(builder)
        "c"
      }
    ))
    testActionsAndResult(expect, builder, expectedResult = "b", numberOfAppends = 2)
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
    e.run() map {
      _ shouldBe 6
    }
  }

  it should "fail if an exception is thrown inside an action" in {
    val expect = constructExpect("", RegexWhen("(LICENSE)".r) (
      Returning[String] { (u: Unit) =>
        appendToBuilder(builder)
        throw new NoSuchElementException()
      }
    ))
    testActionsAndFailedResult(expect, builder)
  }
}
