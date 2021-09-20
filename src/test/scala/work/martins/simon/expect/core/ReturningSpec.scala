package work.martins.simon.expect.core

import scala.util.matching.Regex.Match
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AsyncFlatSpec
import work.martins.simon.expect.TestUtils
import work.martins.simon.expect.core.actions.*

class ReturningSpec extends AsyncFlatSpec with TestUtils with BeforeAndAfterEach:
  val builder = new StringBuilder("")
  val expectedValue = "ReturnedValue"
  
  override protected def beforeEach(): Unit = builder.clear()
  
  "An Expect" should "only return the last returning action before an exit but still execute the previous actions" in {
    val expect = constructExpect(defaultValue = "", When("LICENSE".r)(
      Returning { m =>
        appendToBuilder(builder)
        m.group(0)
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
        When("For details type `warranty'.")(
          Sendln("1 + 2")
        )
      ),
      ExpectBlock(
        When("""\n(\d+)\n""".r)(
          Sendln { m =>
            val previousAnswer = m.group(1)
            s"$previousAnswer + 3"
          }
        )
      ),
      ExpectBlock(
        When("""\n(\d+)\n""".r)(
          Returning(_.group(1).toInt)
        )
      )
    )
    e.run() map {
      _ shouldBe 6
    }
  }
  
  it should "fail if an exception is thrown inside an action" in {
    val expect = constructExpect(defaultValue = "", When("(LICENSE)".r) {
      Returning[Nothing] {
        appendToBuilder(builder)
        throw new NoSuchElementException()
      }
    })
    testActionsAndFailedResult(expect, builder)
  }