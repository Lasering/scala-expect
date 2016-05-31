package work.martins.simon.expect.core

import java.io.EOFException
import java.util.concurrent.TimeoutException

import org.scalatest._
import work.martins.simon.expect.TestUtils
import work.martins.simon.expect.core.actions._

class WhenSpec extends WordSpec with Matchers with TestUtils {
  "An Expect" when {
    "the stdOut does not match with any When" should {
      "run the actions in the TimeoutWhen if one exists" in {
        val e = new Expect("bc -i", defaultValue = "")(
          ExpectBlock (
            StringWhen("Is there anybody out there?") (
              Returning("Just nod if you can hear me.")
            ),
            TimeoutWhen(
              Returning("Is there anyone at home?")
            )
          )
        )
        e.futureValue shouldBe "Is there anyone at home?"
      }
      "fail with TimeoutException if no TimeoutWhen exists" in {
        val e = new Expect("bc -i", defaultValue = "")(
          ExpectBlock (
            StringWhen("Come on, now,") (
              Returning("I hear you're feeling down.")
            )
          )
        )
        e.failedFutureValue shouldBe a [TimeoutException]
      }
      "fail if an exception is thrown inside the TimeoutWhen" in {
        val e = new Expect("bc -i", defaultValue = "")(
          ExpectBlock (
            StringWhen("Is there anybody out there?") (
              Returning("Just nod if you can hear me.")
            ),
            TimeoutWhen(
              Returning { (u: Unit) =>
                throw new IllegalArgumentException()
              }
            )
          )
        )
        e.failedFutureValue shouldBe a [IllegalArgumentException]
      }
    }

    "eof is read from stdOut" should {
      "run the actions in the EndOfFileWhen if one exists" in {
        val e = new Expect("ls", defaultValue = "")(
          ExpectBlock (
            StringWhen("Well I can ease your pain") (
              Returning("Get you on your feet again.")
            ),
            EndOfFileWhen(
              Returning("Relax.")
            )
          )
        )
        e.futureValue shouldBe "Relax."
      }
      "fail with EOFException if no EndOfFileWhen exists" in {
        val e = new Expect("ls", defaultValue = "")(
          ExpectBlock (
            StringWhen("I'll need some information first.") (
              Returning("Just the basic facts.")
            )
          )
        )
        e.failedFutureValue shouldBe a [EOFException]
      }
      "fail if an exception is thrown inside the EndOfFileWhen" in {
        val e = new Expect("ls", defaultValue = "")(
          ExpectBlock (
            StringWhen("Well I can ease your pain") (
              Returning("Get you on your feet again.")
            ),
            EndOfFileWhen(
              Returning { (u: Unit) =>
                throw new IllegalArgumentException()
              }
            )
          )
        )
        e.failedFutureValue shouldBe a [IllegalArgumentException]
      }
    }

    "more than one When matches" should {
      "run the actions in the first matching when" in {
        val e = new Expect("bc -i", defaultValue = "")(
          ExpectBlock(
            RegexWhen("""bc""".r)(
              ReturningWithRegex(_.group(0))
            ),
            StringWhen("bc")(
              Returning("Ohh no")
            )
          )
        )

        e.futureValue should not be "Ohh no"
      }
    }
  }
}
