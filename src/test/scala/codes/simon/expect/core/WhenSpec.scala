package codes.simon.expect.core

import java.io.EOFException
import java.util.concurrent.TimeoutException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{WordSpec, Matchers}

class WhenSpec extends WordSpec with Matchers with ScalaFutures {
  val defaultPatience = PatienceConfig(
    timeout = Span(1, Seconds)
  )

  "An Expect " when {
    "the stdOut does not match with any When" should {
      "run the actions in the TimeoutWhen if one exists" in {
        val e = new Expect("scala", "")(
          new ExpectBlock (
            new StringWhen("Is there anybody out there?") (
              Returning(() => "Just nod if you can hear me.")
            ),
            new TimeoutWhen(
              Returning(() => "Is there anyone at home?")
            )
          )
        )
        e.run(timeout = 500.millis).futureValue(defaultPatience) shouldBe "Is there anyone at home?"
      }
      "fail with TimeoutException if no TimeoutWhen exists" in {
        val e = new Expect("scala", "")(
          new ExpectBlock (
            new StringWhen("Come on, now,") (
              Returning(() => "I hear you're feeling down.")
            )
          )
        )
        e.run(timeout = 500.millis).failed.futureValue(defaultPatience) shouldBe a [TimeoutException]
      }
    }

    "eof is read from stdOut" should {
      "run the actions in the EndOfFileWhen if one exists" in {
        val e = new Expect("ls", "")(
          new ExpectBlock (
            new StringWhen("Well I can ease your pain") (
              Returning(() => "Get you on your feet again.")
            ),
            new EndOfFileWhen(
              Returning(() => "Relax.")
            )
          )
        )
        e.run().futureValue(defaultPatience) shouldBe "Relax."
      }
      "fail with EOFException if no EndOfFileWhen exists" in {
        val e = new Expect("ls", "")(
          new ExpectBlock (
            new StringWhen("I'll need some information first.") (
              Returning(() => "Just the basic facts.")
            )
          )
        )
        e.run().failed.futureValue(defaultPatience) shouldBe a [EOFException]
      }
    }

    "more than one When matches" should {
      "run the actions in the first matching when" in {
        val e = new Expect("scala", "")(
          new ExpectBlock (
            new RegexWhen("""Scala version (\d+\.\d+\.\d+)""".r) (
              ReturningWithRegex(_.group(1))
            ),
            new StringWhen("Scala version")(
              Returning(() => "Ohh no")
            )
          )
        )

        e.run().futureValue(defaultPatience) should not be "Ohh no"
      }
    }
  }
}
