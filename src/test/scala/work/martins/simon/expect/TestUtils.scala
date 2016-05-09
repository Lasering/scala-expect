package work.martins.simon.expect

import scala.concurrent.duration.DurationInt
import org.scalatest.concurrent.ScalaFutures
import work.martins.simon.expect.core.Expect
import scala.concurrent.ExecutionContext.Implicits.global

trait TestUtils extends ScalaFutures { test =>
  implicit class RichExpect[T](expect: Expect[T]) {
    val patienceConfig = PatienceConfig(
      timeout = scaled(expect.settings.timeout + 1.second),
      interval = scaled(500.millis)
    )

    def failedFutureValue: Throwable = expect.run().failed.futureValue(patienceConfig)
    def futureValue: T = expect.run().futureValue(patienceConfig)
    def whenReady[U](f: T => U): U = test.whenReady(expect.run())(f)(patienceConfig)
  }
}
