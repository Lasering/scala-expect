package codes.simon.expect.core

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex.Match

class ScalaSpec extends FlatSpec with Matchers with ScalaFutures {
  val defaultPatience = PatienceConfig(
    timeout = Span(Constants.Timeout.toSeconds + 2, Seconds),
    interval = Span(Constants.Timeout.toSeconds, Seconds)
  )

  "An Expect " should "returned the specified value" in {
    val e = new Expect("scala", "")(
      new ExpectBlock(
        new RegexWhen("""Scala version (\d+\.\d+\.\d+)""".r,
          ReturningWithRegex{ m: Match =>
            m.group(1)
          }
        )
      )
    )
    e.run().futureValue(defaultPatience) should be (util.Properties.versionNumberString)
  }

  it should "be able to interact with the spawned program" in {
    val e = new Expect("scala", 5)(
      new ExpectBlock(
        new StringWhen("scala>",
          Send("1 + 2\n")
        )
      ),
      new ExpectBlock(
        new RegexWhen("""res\d+: Int = (\d+)""".r,
          ReturningWithRegex{ m: Match =>
            m.group(1).toInt
          }
        )
      )
    )
    e.run().futureValue(defaultPatience) should be (Some(3))
  }

  /*import codes.simon.expect.dsl.{Expect => DExpect}
  new DExpect("bc", Option.empty[Int]) {
    expect("For details type `warranty'."){
      sendln("1+2")
    }
    expect("""(\d+)""".r) {
      returning{ m: Match =>
        Some(m.group(1).toInt)
      }
    }
  }*/
}
