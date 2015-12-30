package work.martins.simon.expect.dsl

import work.martins.simon.expect.core.EndOfFile

import scala.concurrent.Await

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

object ScalaSpec extends App {
  //Just like with the fluent.Expect we do not need to test execution of Expects in the dsl package.
  //We just need to test that the generated core.Expects are the correct ones.

  /*val e = new Expect("scala", defaultValue = 5) {
    expect("scala>") {
      sendln("1 + 2")
      returning(_.group(1).toInt)
    }
    expect( """res0: Int = (\d+)""".r) {
      returning(_.group(1).toInt)
    }
  }*/
}
