package work.martins.simon.expect.core

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

object Main {
  def main(args: Array[String]): Unit = {

    val e = new Expect("scala", "")(
      new ExpectBlock (
        new StringWhen("Scala version") (
          Returning(() => "ReturnedValue")
        )
      )
    )
    println(Await.result(e.run(timeout = 10.seconds, redirectStdErrToStdOut = true), 10.seconds))
  }
}
