package work.martins.simon.expect.fluent

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App {
  val e = new Expect("bc -i", defaultValue = 5)
  e.expect("For details type `warranty'.")
    .sendln("5 '")
  e.expect("illegal character: '")
    .returning {
      println("teste")
      5
    }
  val f = e.run()
  Await.result(f, 5.minutes)
}
