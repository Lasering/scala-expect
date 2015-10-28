package codes.simon.expect.core

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

object Main {
  def main(args: Array[String]) = {

    val e = new Expect("scala", "")(
      new ExpectBlock (
        new StringWhen("Scala version") (
          Returning(() => "ReturnedValue")
        )
      )
    )
    /*val e = new Expect("scala", 5)(
      new ExpectBlock(
        new StringWhen("scala>")(
          Send("1 + 2\n")
        )
      ),
      new ExpectBlock(
        new RegexWhen("""res0: Int = (\d+)""".r)(
          ReturningWithRegex(_.group(1).toInt)
        )
      )
    )*/
    println(Await.result(e.run(timeout = 10.seconds, redirectStdErrToStdOut = true), 10.seconds))
  }
}
