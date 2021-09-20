package work.martins.simon.expect.fluent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*
import work.martins.simon.expect.{Settings, core}

class EmptySpec extends AnyFlatSpec with Matchers:
  "An Expect without a command" should "throw IllegalArgumentException" in {
    an [IllegalArgumentException] should be thrownBy new Expect("", defaultValue = ())
  }
  
  "An Expect with an empty expect block" should "fail when generating the core.Expect" in {
    val fe = new Expect(Seq("ls"), defaultValue = (), Settings()) {
      expectBlock
    }
    an [IllegalArgumentException] should be thrownBy fe.toCore
  }
  
  "Invoking expectBlock.expectBlock" should "fail when generating the core.Expect" in {
    val fe = new Expect(Seq("ls"), defaultValue = (), Settings.fromConfig()) {
      expectBlock.expectBlock
    }
    an [IllegalArgumentException] should be thrownBy fe.toCore
  }
  
  "An Expect without expect blocks" should "generate the correct core.Expect" in {
    val fe = new Expect("ls", defaultValue = (), Settings())
    fe.toCore shouldEqual new core.Expect("ls", defaultValue = ())()
  }