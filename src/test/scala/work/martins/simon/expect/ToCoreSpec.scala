package work.martins.simon.expect

import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.*
import work.martins.simon.expect.core.actions.*
import work.martins.simon.expect.fluent.{given, *}
import work.martins.simon.expect.dsl.given

class ToCoreSpec extends AnyWordSpecLike with Matchers:
  def dslSendAndExit(e: dsl.Expect[String])(using When[String]): Unit =
    import e._
    send("string1")
    exit()
  def dslAddRegexWhen(e: dsl.Expect[String])(using ExpectBlock[String]): Unit =
    import e._
    when("""(\d+) \w+""".r) {
      sendln("string2")
    }
  def dslAddWhensEOFAndTimeout(e: dsl.Expect[String])(using ExpectBlock[String]): Unit =
    import e._
    when(EndOfFile) {
      exit()
    }
    when(Timeout) {
      exit()
    }
  
  def fluentSendAndExit(when: fluent.StringWhen[String]): fluent.StringWhen[String] = {
    when
      .send("string1")
      .exit()
  }
  def fluentAddRegexWhen(eb: fluent.ExpectBlock[String]): fluent.RegexWhen[String] = {
    eb.when("""(\d+) \w+""".r)
      .sendln("string2")
  }
  def fluentAddWhensEOFAndTimeout(eb: fluent.ExpectBlock[String]): fluent.When[String] = {
    eb.when(EndOfFile)
        .exit()
      .when(Timeout)
        .exit()
  }
  
  val wrongExpects: Seq[dsl.Expect[String]] = Seq(
    new dsl.Expect(Seq("ls"), defaultValue = "") {
      expectBlock {
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
    new dsl.Expect(Seq("ls"), defaultValue = "", Settings.fromConfig()) {
      expectBlock {
        when("1") {
          send("string1")
          exit()
        }
        //Missing a when
      }
    },
    new dsl.Expect("ls", defaultValue = "", Settings()) {
      expectBlock {
        when("1"){
          send("string1")
          //Missing an action
        }
      }
      expectBlock {
        when(""){}
      }
    },
    new dsl.Expect("ls", defaultValue = "") {
      expectBlock {
        when("1") {
          send("string1")
          exit()
        }
        when("""(\d+) \w+""".r) {
          sendln(m => s"string${m.group(1)}") //Different action
        }
      }
      expectBlock {
        when(""){}
      }
    },
    new dsl.Expect("ls", defaultValue = "") {
      expectBlock {
        when("1") {
          send("string1")
          returning("result") //Different action
        }
      }
      expectBlock {
        when(""){}
      }
    },
    new dsl.Expect("ls", defaultValue = "") {
      expectBlock {
        when("1") {
          send("string1")
          returningExpect(new dsl.Expect("ls", "")) //Different action
        }
      }
      expectBlock {
        when(""){}
      }
    },
    new dsl.Expect("ls", defaultValue = "") {
      expectBlock {
        when("1") {
          send("string1")
          exit()
        }
        when("""(\d+) \w+""".r) {
          returning(_ => "result") //Different action
        }
      }
      expectBlock {
        when(""){}
      }
    },
    new dsl.Expect("ls", defaultValue = "") {
      expectBlock {
        when("1") {
          send("string1")
          exit()
        }
        when("""(\d+) \w+""".r) {
          returningExpect(m => new dsl.Expect(Seq("ls"), m.group(1))) //Different action
        }
      }
      expectBlock {
        when(""){}
      }
    },
    new dsl.Expect("ls", defaultValue = "") {
      expectBlock {
        when("1") {
          send("string1")
          exit()
        }
        when("1"){} //StringWhen instead of RegexWhen
      }
      expectBlock {
        when(""){}
      }
    },
    new dsl.Expect("ls", defaultValue = "") {
      expectBlock {
        //RegexWhen instead of StringWhen
        when("1".r){}
        when("""(\d+) \w+""".r){}
      }
      expectBlock {
        when(""){}
      }
    },
    new dsl.Expect("ls", defaultValue = "") {
      expectBlock {
        when(EndOfFile){} //EndOfFile instead of StringWhen
        when("""(\d+) \w+""".r){}
      }
      expectBlock {
        when(""){}
      }
    },
    new dsl.Expect("ls", defaultValue = "") {
      expectBlock {
        when(Timeout){} //Timeout instead of StringWhen
        when("""(\d+) \w+""".r){}
      }
      expectBlock {
        when(""){}
      }
    },
    new dsl.Expect("ls", defaultValue = "") {
      expectBlock {
        when("".r){
          returning("text")
        }
      }
    }
  )
  
  "An expect" when {
    "multiple actions without functions are added" should {
      val coreExpect = new core.Expect("ls", defaultValue = "")(
        core.ExpectBlock(
          core.When("1")(
            Send("string1"),
            Exit()
          ),
          core.When("""(\d+) \w+""".r)(
            Sendln("string2")
          )
        ),
        core.ExpectBlock(
          core.When(EndOfFile)(
            Exit()
          ),
          core.When(Timeout)(
            Exit()
          )
        )
      )
      
      "generate the correct core.Expect from a dsl.Expect" in {
        val dslExpect = new dsl.Expect("ls", defaultValue = "") {
          expectBlock {
            when("1") {
              addActions(dslSendAndExit)
            }
            addWhen(dslAddRegexWhen)
          }
          expectBlock {
            addWhens(dslAddWhensEOFAndTimeout)
          }
        }
        dslExpect.toCore shouldEqual coreExpect
      }
      "generate the correct core.Expect from a fluent.Expect" in {
        val fluentExpect = new fluent.Expect("ls", defaultValue = "") {
          expectBlock
            .when("1")
              .addActions(fluentSendAndExit)
            .addWhen(fluentAddRegexWhen)
          expectBlock
            .addWhens(fluentAddWhensEOFAndTimeout)
        }
        fluentExpect.toCore shouldEqual coreExpect
      }
      "not generate an equal core.Expect if the expects are different" in {
        wrongExpects.map(_.toCore == coreExpect) should contain only false
      }
    }
    
    // We cannot test that a dsl.Expect generates the correct core.Expect when an Action that contains
    // a function (e.g. Returning, SendWithRegex, etc) is used, this happens because
    // equality on a function is not defined, which leads to the equals operation on the core.Expect to fail.
    // http://stackoverflow.com/questions/20390293/function-equality-notion/20392230#20392230
    // In these cases we just test for structural equality.
    "multiple actions with functions are added" should {
      val coreExpect = new core.Expect("ls", defaultValue = "")(
        core.ExpectBlock(
          core.When("1")(
            Sendln("string1"),
            Exit()
          ),
          core.When("""(\d+) (\w+)""".r)(
            Send(m => s"string${m.group(1)}"),
            Returning(m => s"someOtherValue" * m.group(1).toInt),
            ReturningExpect(m => new core.Expect(s"ls ${m.group(2)}", defaultValue = "")())
          )
        ),
        core.ExpectBlock(
          core.When(EndOfFile)(
            ReturningExpect(new core.Expect("ls", defaultValue = "")())
          ),
          core.When(Timeout)(
            Returning("someValue"),
            Exit()
          )
        )
      )
      
      "generate a structurally equal core.Expect" in {
        val fluentExpect = new fluent.Expect("ls", defaultValue = "") {
          expectBlock
            .when("1")
              .addActions(fluentSendAndExit)
            .when("""(\d+) (\w+)""".r)
              .send(_ => s"Hey this is not the same!")
              .returning(_ => "someOtherValue")
              .returningExpect(_ => new fluent.Expect("bc", defaultValue = "this is also different"))
          expectBlock
            .when(EndOfFile)
              .returningExpect(new fluent.Expect("bc", defaultValue = "this is also different"))
            .when(Timeout)
              .returning("someValue")
              .exit()
        }
        fluentExpect.structurallyEquals(coreExpect) shouldBe true
        
        val dslExpect = new dsl.Expect("ls", defaultValue = "") {
          expectBlock {
            when("1") {
              addActions(dslSendAndExit)
            }
            when("""(\d+) (\w+)""".r) {
              send(_ => "Hey this is not the same!")
              returning(_ => "someOtherValue")
              returningExpect(_ => new dsl.Expect("bc", defaultValue = "this is also different"))
            }
          }
          expectBlock {
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
      }
      "not generate a structurally equal core.Expect if the expects are different" in {
        wrongExpects.map(_.structurallyEquals(coreExpect)) should contain only false
        
        //Test structurally equals on ActionReturningAction
        val ara: core.Expect[String] = new dsl.Expect("ls", "") {
          expectBlock {
            when("".r) {
              returning("text")
            }
          }
        }.transform(
          { case "" => new core.Expect("ls", "")() },
          { case "text" => "diferentText" }
        )
        
        //Test structurally equals on ActionReturningActionWithRegex
        val araWithRegex: core.Expect[String] = new dsl.Expect("ls", "") {
          expectBlock {
            when("".r) {
              returning(_ => "text")
            }
          }
        }.transform(
          { case "" => new core.Expect("ls", "")() },
          { case "text" => "I see what you did here" }
        )
        
        wrongExpects.map(ara.structurallyEquals(_)) should contain only false
        wrongExpects.map(araWithRegex.structurallyEquals(_)) should contain only false
      }
    }
  }