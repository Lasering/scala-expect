package work.martins.simon.expect

import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.matchers.should.*
import work.martins.simon.expect.core.*
import work.martins.simon.expect.core.actions.*


class SettingsSpec extends AsyncWordSpec with Matchers:
  "Settings" when {
    "wrong options are specified" should {
      "throw IllegalArgumentException if timeFactor is < 1" in {
        val ex = the [IllegalArgumentException] thrownBy {
          Settings(timeoutFactor = -25)
        }
        ex.getMessage should include("Time factor must be >=1")
      }
      "throw IllegalArgumentException if timeFactor is NaN" in {
        val ex = the [IllegalArgumentException] thrownBy {
          Settings(timeoutFactor = Double.NaN)
        }
        ex.getMessage should include("not Infinity or NaN.")
      }
      "throw IllegalArgumentException if timeFactor is Infinity" in {
        val ex1 = the [IllegalArgumentException] thrownBy {
          Settings(timeoutFactor = Double.PositiveInfinity)
        }
        ex1.getMessage should include("not Infinity or NaN.")
        val ex2 = the [IllegalArgumentException] thrownBy {
          Settings(timeoutFactor = Double.NegativeInfinity)
        }
        ex2.getMessage should include("not Infinity or NaN.")
      }
    }
    "created via TypeSafe config" should {
      "have the same default values as the domain class" in {
        Settings.fromConfig() shouldBe new Settings()
        Settings.fromConfig() shouldBe Settings()
      }
    }
    
    import scala.concurrent.duration.DurationInt
    val settings = Settings(timeout = 1.minute, timeoutFactor = 1.5)
    
    "passed to the Expect constructor" should {
      "be used" in {
        new Expect(Seq("ls"), (), settings)().settings shouldBe settings
        new Expect("ls", (), settings)().settings shouldBe settings
      }
    }
    "passed to the run method" should {
      "override the setttings passed in the constructor" in {
        val expect = new Expect(Seq("ls"), "", settings)(
          ExpectBlock(
            When(".+".r)(
              Returning("Found something")
            ),
            When(Timeout)(
              Returning("Timeout")
            )
          )
        )
        // With the default settings it works as expected :P
        expect.run().map {
          _ shouldBe "Found something"
        }
        
        // We cannot directly observe whether the settings are in fact being overridden.
        // So we hide behind causing a timeout to test that they are.
        expect.run(Settings(timeout = 1.nano)).map {
          _ shouldBe "Timeout"
        }
      }
    }
  }