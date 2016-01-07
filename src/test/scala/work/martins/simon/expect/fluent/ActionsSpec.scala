package work.martins.simon.expect.fluent

import org.scalatest.{WordSpec, Matchers}
import work.martins.simon.expect.core
import work.martins.simon.expect.core._

import scala.util.matching.Regex.Match

//The execution of a fluent.Expect is delegated to core.Expect
//This means fluent.Expect does not know how to execute Expects.
//So there isn't a need to test execution of Expects in the fluent package.
//There is, however, the need to test that the core.Expect generated from a fluent.Expect is the correct one.

class ActionsSpec extends WordSpec with Matchers {
  "An Expect" when {
    //We cannot test that a fluent.Expect generates the correct core.Expect when an Action that contains
    //a function (e.g. Returning, ReturningWithRegex, SendWithRegex, etc) is used, this happens because
    //equality on a function is not defined, which leads to the equals operation on the core.Expect to fail.
    //http://stackoverflow.com/questions/20390293/function-equality-notion/20392230#20392230
    //In these cases we just test for structural equality.

    "multiple actions without functions are added" should {
      "generate the correct core.Expect" in {
        val coreExpect = new core.Expect("ls", defaultValue = "")(
          new core.ExpectBlock(
            new core.StringWhen("1")(
              Send("string1"),
              Exit
            )
          ),
          new core.ExpectBlock(
            new core.RegexWhen("""(\d+) \w+""".r)(
              Sendln("string2")
            ),
            new core.RegexWhen("""(\d+ \w+)""".r)(
              Exit
            )
          )
        )

        val fluentExpect = new Expect("ls", defaultValue = "") {
          expect
            .when("1")
              .send("string1")
              .exit()
          expect
            .when("""(\d+) \w+""".r)
              .sendln("string2")
            .when("""(\d+ \w+)""".r)
              .exit()
        }

        fluentExpect.toCore shouldEqual coreExpect
      }
    }
    "multiple actions with functions are added" should {
      "generate a structurally correct core.Expect" in {
        val coreExpect = new core.Expect("ls", defaultValue = "")(
          new core.ExpectBlock(
            new core.StringWhen("1")(
              Sendln("string1"),
              Returning(() => "string2"),
              Exit
            )
          ),
          new core.ExpectBlock(
            new core.RegexWhen("""(\d+) \w+""".r)(
              SendlnWithRegex { m: Match =>
                val i = m.group(1)
                s"string$i"
              }
            ),
            new core.RegexWhen("""(\d+ \w+)""".r)(
              ReturningWithRegex(_.group(1)),
              Exit
            )
          )
        )

        val fluentExpect = new Expect("ls", defaultValue = "") {
          expect
            .when("1")
              .sendln("string1")
              .returning("string2")
              .exit()
          expect
            .when("""(\d+) \w+""".r)
              .sendln { m: Match =>
                val i = m.group(1)
                s"string$i"
              }
            .when("""(\d+ \w+)""".r)
              .returning(_.group(1))
              .exit()
        }

        fluentExpect.toCore.structurallyEquals(coreExpect) shouldBe true
      }
    }
  }
}
