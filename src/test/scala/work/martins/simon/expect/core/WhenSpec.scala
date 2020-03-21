package work.martins.simon.expect.core

import java.io.EOFException
import java.util.concurrent.TimeoutException

import org.scalatest._
import work.martins.simon.expect.{EndOfFile, TestUtils, Timeout}
import work.martins.simon.expect.core.actions._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class WhenSpec extends AsyncWordSpec with Matchers with TestUtils {
  "An Expect" when {
    "the stdOut does not match with any When" should {
      "run the actions in the TimeoutWhen" in {
        val e = new Expect("bc -i", defaultValue = "")(
          ExpectBlock (
            When("Is there anybody out there?") (
              Returning("Just nod if you can hear me.")
            ),
            When(Timeout)(
              Returning("Is there anyone at home?")
            )
          )
        )
        e.run() map {
          _ shouldBe "Is there anyone at home?"
        }
      }
      "fail with TimeoutException if no TimeoutWhen exists" in {
        val e = new Expect("bc -i", defaultValue = "")(
          ExpectBlock (
            When("Come on, now,") (
              Returning("I hear you're feeling down.")
            )
          ),
          ExpectBlock (
            When(Timeout)(
              //The expect will never reach this because a TimeoutException will be thrown in the previous expect block
              Returning("can't reach this")
            )
          )
        )
        e.run().failed map {
          _ shouldBe a[TimeoutException]
        }
      }
      "fail with the exception thrown inside the TimeoutWhen" in {
        val e = new Expect("bc -i", defaultValue = "")(
          ExpectBlock (
            When("Is there anybody out there?") (
              Returning("Just nod if you can hear me.")
            ),
            When(Timeout)(
              Returning(throw new IllegalArgumentException())
            )
          )
        )
        e.run().failed map {
          _ shouldBe a[IllegalArgumentException]
        }
      }
    }

    "eof is read from stdOut" should {
      "run the actions in the EndOfFileWhen" in {
        val e = new Expect("ls", defaultValue = "")(
          ExpectBlock (
            When("Well I can ease your pain") (
              Returning("Get you on your feet again.")
            ),
            When(EndOfFile)(
              Returning("Relax.")
            )
          )
        )
        e.run() map {
          _ shouldBe "Relax."
        }
      }
      "fail with EOFException if no EndOfFileWhen exists" in {
        val e = new Expect("ls", defaultValue = "")(
          ExpectBlock (
            When("I'll need some information first.") (
              Returning("Just the basic facts.")
            )
          ),
          ExpectBlock (
            When(EndOfFile)(
              //The expect will never reach this because a EOFException will be thrown in the previous expect block
              Returning("can't reach this")
            )
          )
        )
        e.run().failed map {
          _ shouldBe a[EOFException]
        }
      }
      "fail with the exception thrown inside the EndOfFileWhen" in {
        val e = new Expect("ls", defaultValue = "")(
          ExpectBlock (
            When("Well I can ease your pain") (
              Returning("Get you on your feet again.")
            ),
            When(EndOfFile)(
              Returning(throw new IllegalArgumentException())
            )
          )
        )
        e.run().failed map {
          _ shouldBe a[IllegalArgumentException]
        }
      }
    }

    "more than one When matches" should {
      "run the actions in the first matching when" in {
        val e = new Expect("bc -i", defaultValue = "")(
          ExpectBlock(
            When("""bc""".r)(
              Returning(_.group(0))
            ),
            When("bc")(
              Returning("Ohh no")
            )
          )
        )
  
        e.run() map {
          _ should not be "Ohh no"
        }
      }
    }
  }
}
