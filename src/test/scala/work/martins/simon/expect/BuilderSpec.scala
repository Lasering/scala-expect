package work.martins.simon.expect

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}
import work.martins.simon.expect.core.actions._
import work.martins.simon.expect.dsl.dslToCoreExpect

class BuilderSpec extends WordSpec with Matchers {
  def dslSendAndExit(e: dsl.Expect[String]): Unit = {
    import e._
    send("string1")
    exit()
  }
  def dslAddBlock(e: dsl.Expect[String]): Unit = {
    import e._
    e.expect {
      addWhens(dslAddWhensEOFAndTimeout)
    }
  }
  def dslAddRegexWhen(e: dsl.Expect[String]): Unit = {
    import e._
    when("""(\d+) \w+""".r) {
      sendln("string2")
    }
  }
  def dslAddWhensEOFAndTimeout(e: dsl.Expect[String]): Unit = {
    import e._
    when(EndOfFile) {
      exit()
    }
    when(Timeout) {
      exit()
    }
  }

  def fluentSendAndExit(when: fluent.StringWhen[String]): Unit = {
    when
      .send("string1")
      .exit()
  }
  def fluentAddBlock(e: fluent.Expect[String]): Unit = {
    e.expect
      .addWhens(fluentAddWhensEOFAndTimeout)
  }
  def fluentAddRegexWhen(eb: fluent.ExpectBlock[String]): fluent.RegexWhen[String] = {
    eb.when("""(\d+) \w+""".r)
      .sendln("string2")
  }
  def fluentAddWhensEOFAndTimeout(eb: fluent.ExpectBlock[String]): Unit = {
    eb.when(EndOfFile)
        .exit()
      .when(Timeout)
        .exit()
  }

  val wrongExpects = Seq(
    new dsl.Expect(Seq("ls"), defaultValue = "") {
      expect {
        when("1") {
          send("string1")
          exit()
        }
        when("""(\d+) \w+""".r) {
          sendln("string2")
        }
      }
      //Missing a expect block
    },
    new dsl.Expect(Seq("ls"), defaultValue = "", ConfigFactory.load()) {
      expect {
        when("1") {
          send("string1")
          exit()
        }
        //Missing a when
      }
    },
    new dsl.Expect("ls", defaultValue = "", ConfigFactory.load()) {
      expect("1"){
        send("string1")
        //Missing an action
      }
      expect(""){}
    },
    new dsl.Expect("ls", defaultValue = "") {
      expect {
        when("1") {
          send("string1")
          exit()
        }
        when("""(\d+) \w+""".r) {
          sendln(m => s"string${m.group(1)}") //Different action
        }
      }
      expect(""){}
    },
    new dsl.Expect("ls", defaultValue = "") {
      expect("1"){
        send("string1")
        returning("result") //Different action
      }
      expect(""){}
    },
    new dsl.Expect("ls", defaultValue = "") {
      expect("1") {
        send("string1")
        returningExpect(new dsl.Expect("ls", "")) //Different action
      }
      expect(""){}
    },
    new dsl.Expect("ls", defaultValue = "") {
      expect {
        when("1") {
          send("string1")
          exit()
        }
        when("""(\d+) \w+""".r) {
          returning(m => "result") //Different action
        }
      }
      expect(""){}
    },
    new dsl.Expect("ls", defaultValue = "") {
      expect {
        when("1") {
          send("string1")
          exit()
        }
        when("""(\d+) \w+""".r) {
          returningExpect(m => new dsl.Expect(Seq("ls"), m.group(1), ConfigFactory.load())) //Different action
        }
      }
      expect(""){}
    },
    new dsl.Expect("ls", defaultValue = "") {
      expect {
        when("1") {
          send("string1")
          exit()
        }
        when("1"){} //StringWhen instead of RegexWhen
      }
      expect(""){}
    },
    new dsl.Expect("ls", defaultValue = "") {
      expect {
        //RegexWhen instead of StringWhen
        when("1".r){}
        when("""(\d+) \w+""".r){}
      }
      expect(""){}
    },
    new dsl.Expect("ls", defaultValue = "") {
      expect {
        when(EndOfFile){} //EndOfFile instead of StringWhen
        when("""(\d+) \w+""".r){}
      }
      expect(""){}
    },
    new dsl.Expect("ls", defaultValue = "") {
      expect {
        when(Timeout){} //Timeout instead of StringWhen
        when("""(\d+) \w+""".r){}
      }
      expect(""){}
    },
    new dsl.Expect("ls", defaultValue = "") {
      expect("".r){
        returning("text")
      }
    }
  )

  "An expect" when {
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
        val dslExpect = new dsl.Expect("ls", defaultValue = "") {
          expect {
            when("1") {
              addActions(dslSendAndExit)
            }
            addWhen(dslAddRegexWhen)
          }
          expect {
            addWhens(dslAddWhensEOFAndTimeout)
          }
        }
        dslExpect.toCore shouldEqual coreExpect

        val fluentExpect = new fluent.Expect("ls", defaultValue = "") {
          expect
            .when("1")
              .addActions(fluentSendAndExit)
            .addWhen(fluentAddRegexWhen)
          addExpectBlock(fluentAddBlock)
        }
        fluentExpect.toCore shouldEqual coreExpect
      }
      "not generate an equal core.Expect if the expects are different" in {
        wrongExpects.map(_.toCore == coreExpect) should contain only false

        wrongExpects.map(_.fluentExpect.toCore == coreExpect) should contain only false
      }
    }

    //We cannot test that a dsl.Expect generates the correct core.Expect when an Action that contains
    //a function (e.g. Returning, SendWithRegex, etc) is used, this happens because
    //equality on a function is not defined, which leads to the equals operation on the core.Expect to fail.
    //http://stackoverflow.com/questions/20390293/function-equality-notion/20392230#20392230
    //In these cases we just test for structural equality.
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
            ReturningWithRegex(m => "someOtherValue"),
            ReturningExpectWithRegex(m => new core.Expect(s"ls ${m.group(2)}", defaultValue = "")())
          )
        ),
        core.ExpectBlock(
          core.EndOfFileWhen(
            ReturningExpect(new core.Expect("ls", defaultValue = "")())
          ),
          core.TimeoutWhen(
            Returning("someValue"),
            Exit()
          )
        )
      )

      "generate a structurally equal core.Expect" in {
        val dslExpect = new dsl.Expect("ls", defaultValue = "") {
          expect {
            when("1") {
              addActions(dslSendAndExit)
            }
            when("""(\d+) (\w+)""".r) {
              send(m => s"Hey this is not the same!")
              returning(m => "someOtherValue")
              returningExpect(m => new dsl.Expect("bc", defaultValue = "this is also different", ConfigFactory.load()))
            }
          }
          expect {
            when(EndOfFile) {
              returningExpect(new dsl.Expect("bc", defaultValue = "this is also different"))
            }
            when(Timeout) {
              returning("someValue")
              exit()
            }
          }
        }
        dslExpect.structurallyEquals(coreExpect) shouldBe true

        val fluentExpect = new fluent.Expect("ls", defaultValue = "") {
          expect
            .when("1")
              .addActions(fluentSendAndExit)
            .when("""(\d+) (\w+)""".r)
              .send(m => s"Hey this is not the same!")
              .returning(m => "someOtherValue")
              .returningExpect(m => new fluent.Expect("bc", defaultValue = "this is also different"))
          expect
            .when(EndOfFile)
              .returningExpect(new fluent.Expect("bc", defaultValue = "this is also different"))
            .when(Timeout)
              .returning("someValue")
              .exit()
        }
        fluentExpect.structurallyEquals(coreExpect) shouldBe true
      }
      "not generate a structurally equal core.Expect if the expects are different" in {
        wrongExpects.map(_.structurallyEquals(coreExpect)) should contain only false
        wrongExpects.map(_.fluentExpect.structurallyEquals(coreExpect)) should contain only false

        //Test structurally equals on ActionReturningAction
        val ara: core.Expect[String] = new dsl.Expect("ls", "") {
          expect("".r){
            returning("text")
          }
        }.transform{
          case "" => new core.Expect("ls", "")()
        }{
          case "text" => "diferentText"
        }
        //Test structurally equals on ActionReturningActionWithRegex
        val araWithRegex: core.Expect[String] = new dsl.Expect("ls", "") {
          expect("".r){
            returning(m => "text")
          }
        }.transform{
          case "" => new core.Expect("ls", "")()
        }{
          case "text" => "I see what you did here"
        }

        wrongExpects.map(ara.structurallyEquals(_)) should contain only false
        wrongExpects.map(araWithRegex.structurallyEquals(_)) should contain only false
      }
    }
  }
}
