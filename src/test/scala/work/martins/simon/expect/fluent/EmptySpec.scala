package work.martins.simon.expect.fluent

import org.scalatest.{FlatSpec, Matchers}
import work.martins.simon.expect.core

class EmptySpec extends FlatSpec with Matchers {
  "An Expect without a command" should "throw IllegalArgumentException" in {
    intercept[IllegalArgumentException] {
      new Expect("", defaultValue = ())
    }
  }

  "An Expect without expect blocks" should "generate the correct core.Expect" in {
    val fe = new Expect("ls", defaultValue = ())
    fe.toCore shouldEqual new core.Expect("ls", defaultValue = ())()
  }

  "An Expect with an empty expect block" should "fail while generating the core.Expect" in {
    val fe = new Expect("ls", defaultValue = ()).expect
    intercept[IllegalArgumentException] {
      fe.toCore
    }
  }
}
