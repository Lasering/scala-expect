package codes.simon.expect.core

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.matching.Regex.Match

object Main {
  def main(args: Array[String]) {

    /*val e = new Expect("scala", Option.empty[Int],
      new ExpectBlock(
        new StringWhen[Option[Int]]("scala>",
          Send("1+2\n")
        )
      ),
      new ExpectBlock(
        new RegexWhen[Option[Int]]("""res\d+: Int = (\d+)""".r,
          ReturningWithRegex{ m: Match =>
            Some(m.group(1).toInt)
          }
        )
      )
    )*/
    val e = new Expect("bc", Option.empty[String],
      new ExpectBlock(
        new StringWhen[Option[String]]("For details type",
          Returning(() => Some("True"))
        )
      )
    )
    println(Await.result(e.run(timeout = 2.seconds, redirectStdErrToStdOut = true), 3.seconds))
  }
}
