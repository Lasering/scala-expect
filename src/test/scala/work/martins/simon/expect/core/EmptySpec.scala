package work.martins.simon.expect.core

import java.io.IOException

import com.typesafe.config.ConfigFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import work.martins.simon.expect.TestUtils

class EmptySpec extends AsyncFlatSpec with Matchers with TestUtils {
  "An Expect without a command" should "throw IllegalArgumentException" in {
    assertThrows[IllegalArgumentException] {
      new Expect("", defaultValue = ())()
    }
  }

  "An Expect with a command not available in the system" should "throw IOException" in {
    assertThrows[IOException] {
      new Expect("ã", defaultValue = ())().run()
    }
  }

  "An Expect without expect blocks" should "return the default value" in {
    val defaultValue = "some nice default value"
    val expect = new Expect("ls", defaultValue, ConfigFactory.load())()
    //This is one a cheat to cover the overloaded run method
    expect.run(expect.settings).futureValue shouldBe defaultValue
  }
  it should "map just the default value" in {
    val defaultValue = "some nice default value"
    val e = new Expect("ls", defaultValue)().map(_ * 2)
    e.run() map {
      _ shouldBe (defaultValue * 2)
    }
  }
  it should "flatMap just the default value" in {
    val defaultValue = "some nice default value"
    val e = new Expect("ls", defaultValue)().flatMap(_ => new Expect("ls", defaultValue.split(" ").headOption)())
    e.run() map {
      _ shouldBe defaultValue.split(" ").headOption
    }
  }
  it should "transform just the default value (flatmap)" in {
    val defaultValue = "some nice default value"
    val e = new Expect("ls", defaultValue)()

    val e2 = e.transform {
      case e.defaultValue => new Expect("ls", defaultValue.split(" ").headOption)()
    } {
      case _ => None
    }
    e2.run() map {
      _ should not be None
    }
  }
  it should "transform just the default value (map)" in {
    val defaultValue = "some nice default value"
    val e = new Expect("ls", defaultValue)()
    
    val e2 = e.transform {
      case t if t != e.defaultValue => new Expect("ls", None: Option[String])()
    } {
      case _ => defaultValue.split(" ").headOption
    }
    e2.run() map {
      _ should not be None
    }
  }

  "Transforming when the defaultValue is not in domain" should "throw a NoSuchElementException" in {
    val e = new Expect("ls", "some nice default value")()
    val thrown = the [NoSuchElementException] thrownBy e.transform {
      PartialFunction.empty[String, Expect[String]]
    } {
      PartialFunction.empty[String, String]
    }
    thrown.getMessage should include ("default value")
  }

  "An Expect with an empty expect block" should "fail with IllegalArgumentException" in {
    assertThrows[IllegalArgumentException] {
      new Expect("ls", defaultValue = ())(ExpectBlock())
    }
  }

  "An Expect with an empty when" should "return the default value" in {
    val defaultValue = "some nice default value"
    val e = new Expect(Seq("echo", "ola"), defaultValue, ConfigFactory.load())(
      ExpectBlock(
        StringWhen("ola")()
      )
    )
    e.run() map {
      _ shouldBe defaultValue
    }
  }
}
