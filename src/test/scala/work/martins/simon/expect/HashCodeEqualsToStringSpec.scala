package work.martins.simon.expect

import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core._
import work.martins.simon.expect.core.actions.Send
import work.martins.simon.expect.fluent.{Expect, ExpectBlock, When}

import scala.collection.immutable.HashSet
import scala.util.Random
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HashCodeEqualsToStringSpec extends AnyFlatSpec with Matchers {
  def addSendAndExit[R](when: When[R]): When[R] = {
    when
      .send("text")
      .send("a password", sensitive = true)
      .exit()
  }
  def addBlock(e: fluent.Expect[String]): ExpectBlock[String] = {
    e.expect
      .addWhens(addWhensEOFAndTimeout)
  }
  def addWhensEOFAndTimeout(eb: fluent.ExpectBlock[String]): fluent.TimeoutWhen[String] = {
    eb.when(EndOfFile)
        .exit()
      .when(Timeout)
        .exit()
  }

  val objects: Seq[Any] = Seq(
    Timeout, //The curve ball to test that equals returns false

    //To test equals returns false on expectBlock
    core.ExpectBlock(StringWhen("1")()),
    ExpectBlock(new Expect("ls", "")),

    new Expect("ls", ""),
    new Expect("ls", "") {
      expect
        .when("1")
    },
    new Expect("ls", "") {
      expect
        .when("2".r)
    },
    new dsl.Expect("ls", "") {
      expect{
        when(EndOfFile){}
      }
    },
    new Expect("ls", "") {
      expect
        .when(EndOfFile)
        .when(EndOfFile)
        .when("")
    },
    new Expect("ls", "") {
      expect
        .when(Timeout)
    },
    new Expect("ls", "") {
      expect
        .when("1")
          .addActions(addSendAndExit)
    },
    new Expect("ls", "") {
      expect
        .when("2".r)
          .send("")
          .exit()
    },
    new Expect("ls", "") {
      expect
        .when(EndOfFile)
          .addActions(addSendAndExit)
    },
    new Expect("ls", "") {
      expect
        .when(Timeout)
          .addActions(addSendAndExit)
    },
    new dsl.Expect("ls", "") {
      expect {
        when("") {
          send("")
        }
      }
    },
    new Expect("ls", "") {
      expect
        .when("a".r)
          .addActions(addSendAndExit)
        .addWhens(addWhensEOFAndTimeout)
    },
    new Expect("ls", "") {
      expect
        .when("c".r)
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
      expect
        .when("")
          .send("")
    }

    dslExpect.toString shouldEqual fluentExpect.toString
  }
}
