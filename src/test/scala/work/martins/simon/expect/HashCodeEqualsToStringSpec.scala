package work.martins.simon.expect

import org.scalatest.{FlatSpec, Matchers}
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core._
import work.martins.simon.expect.core.actions.Send
import work.martins.simon.expect.fluent.{Expect, ExpectBlock, When}

import scala.collection.immutable.HashSet
import scala.util.Random

class HashCodeEqualsToStringSpec extends FlatSpec with Matchers {
  def addSendAndExit[R](when: When[R]): Unit = {
    when
      .send("text")
      .send("a password", sensitive = true)
      .exit()
  }
  def addBlock(e: fluent.Expect[String]): Unit = {
    e.expect
      .addWhens(addWhensEOFAndTimeout)
  }
  def addWhensEOFAndTimeout(eb: fluent.ExpectBlock[String]): Unit = {
    eb.when(EndOfFile)
      .exit()
      .when(Timeout)
      .exit()
  }

  val objects = Seq(
    Timeout, //The curve ball to test that equals returns false

    //To test equals returns false on expectBlock
    core.ExpectBlock(StringWhen("1")()),
    new ExpectBlock(new Expect("ls", "")),

    new Expect("ls", ""),
    new Expect("ls", "") {
      expect("1")
    },
    new Expect("ls", "") {
      expect("2".r)
    },
    new dsl.Expect("ls", "") {
      expect(EndOfFile){}
    },
    new Expect("ls", "") {
      expect
        .when(EndOfFile)
        .when(EndOfFile)
        .when("")
    },
    new Expect("ls", "") {
      expect(Timeout)
    },
    new Expect("ls", "") {
      expect("1")
        .addActions(addSendAndExit)
    },
    new Expect("ls", "") {
      expect("2".r)
        .send("")
        .exit()
    },
    new Expect("ls", "") {
      expect(EndOfFile)
        .addActions(addSendAndExit)
    },
    new Expect("ls", "") {
      expect(Timeout)
        .addActions(addSendAndExit)
    },
    new dsl.Expect("ls", "") {
      expect {
        when("") {
          send("")
        }
      }
    },
    new fluent.Expect("ls", "") {
      expect
        .when("a".r)
          .addActions(addSendAndExit)
        .addWhens(addWhensEOFAndTimeout)
    },
    new fluent.Expect("ls", "") {
      expect("c".r)
      .addExpectBlock(addBlock)
    }
  )

  "hashCode and equals" should "work" in {
    val rnd = new Random()
    var set = HashSet.empty[Any] //Tests hashCode
    for(o <- objects) {
      val n = rnd.nextInt(5) + 3 //Insert at least 3
      set ++= Seq.fill(n)(o)
    }

    for(o <- objects) {
      set.count(_ == o) shouldBe 1 //Tests equals
    }

    val objectsWithCoreExpects = objects map {
      case e: Expect[_] => e.toCore
      case e: dsl.Expect[_] => e.toCore
      case e => e
    }
    val setCore = HashSet(objectsWithCoreExpects:_*) //Tests hashCode
    for(o <- objectsWithCoreExpects) {
      setCore should contain (o) //Tests equals
    }
  }

  "toString" should "contain useful information" in {
    val expects = objects.collect{ case e: Expect[_] => e }
    for (expect <- expects) {
      val expectToString = expect.toString
      expectToString should include ("Expect")
      expectToString should include (expect.command.toString)
      expectToString should include (expect.defaultValue.toString)

      val expectCoreToString = expect.toCore.toString
      expectCoreToString should include ("Expect")
      expectCoreToString should include (expect.command.toString)
      expectCoreToString should include (expect.defaultValue.toString)

      val settings = expect.settings
      val settingsToString = settings.toString
      settingsToString should include ("Settings")
      settingsToString should include (settings.timeout.toString)
      settingsToString should include (settings.charset.toString)
      settingsToString should include (settings.redirectStdErrToStdOut.toString)

      for (block <- expect.toCore.expectBlocks) {
        expectToString should include (block.toString.indent())

        val blockToString = block.toString
        blockToString should include ("expect")
        for (when <- block.whens) {
          blockToString should include (when.toString.indent())

          val whenToString = when.toString
          whenToString should include ("when")

          when match {
            case StringWhen(pattern, _) => whenToString should include (pattern)
            case RegexWhen(pattern, _) => whenToString should include (escape(pattern.regex)) //This one is a little cheat
            case EndOfFileWhen(_) => whenToString should include ("EndOfFile")
            case TimeoutWhen() => whenToString should include ("Timeout")
          }

          for (action <- when.actions) {
            whenToString should include (action.toString)
            action.toString should include (action.getClass.getSimpleName)
            action match {
              case Send(_, true) => action.toString should include ("omitted sensitive output")
              case Send(text, false) => action.toString should include (text)
              case _ =>
            }
          }
        }
      }
    }

    val dslExpect = new dsl.Expect("ls", "") {
      expect {
        when("") {
          send("")
        }
      }
    }
    val fluentExpect = new fluent.Expect("ls", "") {
      expect("").send("")
    }

    dslExpect.toString shouldEqual fluentExpect.toString
  }
}
