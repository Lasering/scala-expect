package work.martins.simon.expect.fluent

import org.scalatest.{Matchers, WordSpec}
import work.martins.simon.expect.core.actions._
import work.martins.simon.expect.{EndOfFile, Timeout, core}

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

    val wrongFluentExpects = Seq(
      new Expect("ls", defaultValue = "") {
        expect
          .when("1")
            .send("string1")
            .exit()
          .when("""(\d+) \w+""".r)
            .sendln("string2")
        //Missing a expect block
      },
      new Expect("ls", defaultValue = "") {
        expect
          .when("1")
            .send("string1")
            .exit()
          //Missing a when
      },
      new Expect("ls", defaultValue = "") {
        expect("1")
          .send("string1")
          //Missing an action
        expect("")
      },
      new Expect("ls", defaultValue = "") {
        expect
          .when("1")
            .send("string1")
            .exit()
          .when("""(\d+) \w+""".r)
            .sendln(m => s"string${m.group(1)}") //Different action
        expect("")
      },
      new Expect("ls", defaultValue = "") {
        expect("1")
          .send("string1")
          .returning("result") //Different action
        expect("")
      },
      new Expect("ls", defaultValue = "") {
        expect("1")
          .send("string1")
          .returningExpect(new Expect("ls", "")) //Different action
        expect("")
      },
      new Expect("ls", defaultValue = "") {
        expect
          .when("1")
            .send("string1")
            .exit()
          .when("""(\d+) \w+""".r)
          .returning(m => "result") //Different action
        expect("")
      },
      new Expect("ls", defaultValue = "") {
        expect
          .when("1")
            .send("string1")
            .exit()
          .when("""(\d+) \w+""".r)
            .returningExpect(m => new Expect("ls", m.group(1))) //Different action
        expect("")
      },
      new Expect("ls", defaultValue = "") {
        expect
          .when("1")
            .send("string1")
            .exit()
          .when("1") //StringWhen instead of RegexWhen
        expect("")
      },
      new Expect("ls", defaultValue = "") {
        expect
          .when("1".r) //RegexWhen instead of StringWhen
          .when("""(\d+) \w+""".r)
        expect("")
      },
      new Expect("ls", defaultValue = "") {
        expect
          .when(EndOfFile) //EndOfFile instead of StringWhen
          .when("""(\d+) \w+""".r)
        expect("")
      },
      new Expect("ls", defaultValue = "") {
        expect
          .when(Timeout) //Timeout instead of StringWhen
          .when("""(\d+) \w+""".r)
        expect("")
      }
    )

    def sendAndExit(when: StringWhen[String]): Unit = {
      when
        .send("string1")
        .exit()
    }

    "multiple actions without functions are added" should {
      val coreExpect = new core.Expect("ls", defaultValue = "")(
        core.ExpectBlock(
          core.StringWhen("1")(
            Send("string1"),
            Exit()
          ),
          core.RegexWhen("""(\d+) \w+""".r)(
            Sendln("string2")
          )
        ),
        core.ExpectBlock(
          core.EndOfFileWhen(
            Exit()
          ),
          core.TimeoutWhen(
            Exit()
          )
        )
      )

      "generate the correct core.Expect" in {
        val fluentExpect = new Expect("ls", defaultValue = "") {
          expect
            .when("1")
              .addActions(sendAndExit)
            .when("""(\d+) \w+""".r)
              .sendln("string2")
          expect
            .when(EndOfFile)
              .exit()
            .when(Timeout)
              .exit()
        }

        fluentExpect.toCore shouldEqual coreExpect
      }

      "not generate an equal core.Expect if they are different" in {
        wrongFluentExpects.map(_.toCore == coreExpect) should contain only false
      }
    }
    "multiple actions with functions are added" should {
      val coreExpect = new core.Expect("ls", defaultValue = "")(
        core.ExpectBlock(
          core.StringWhen("1")(
            Sendln("string1"),
            Exit()
          ),
          core.RegexWhen("""(\d+) (\w+)""".r)(
            SendWithRegex { m =>
              val i = m.group(1)
              s"string$i"
            },
            ReturningExpectWithRegex(m => new core.Expect(s"ls ${m.group(2)}", defaultValue = "")())
          )
        ),
        core.ExpectBlock(
          core.EndOfFileWhen(
            ReturningExpect(new core.Expect("ls", defaultValue = "")())
          ),
          core.TimeoutWhen(
            Exit()
          )
        )
      )

      "generate a structurally equal core.Expect" in {
        val fluentExpect = new Expect("ls", defaultValue = "") {
          expect
            .when("1")
              .addActions(sendAndExit)
            .when("""(\d+) (\w+)""".r)
              .send(m => s"Hey this is not the same!")
              .returningExpect(m => new core.Expect("bc", defaultValue = "this is also different")())
          expect
            .when(EndOfFile)
              .returningExpect(new core.Expect("bc", defaultValue = "this is also different")())
            .when(Timeout)
              .exit()
        }

        fluentExpect.structurallyEquals(coreExpect) shouldBe true
      }

      "not generate a structurally equal core.Expect if they are different" in {
        wrongFluentExpects.map(_.structurallyEquals(coreExpect)) should contain only false
      }
    }
  }
}
