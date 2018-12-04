package work.martins.simon.expect.core

import java.io.IOException

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
      new Expect("存在しない", defaultValue = ())().run()
    }
  }

  val defaultValue = "some nice default value"
  val baseExpect = new Expect("ls", defaultValue)()
  def baseExpectWithFunction[T](f: String => T)(t: String): Expect[T] = new Expect("ls", f(t))()
  def firstWord(value: String): Option[String] = value.split(" ").headOption

  "An Expect without expect blocks" should "return the default value" in {
    baseExpect.run().map {
      _ shouldBe defaultValue
    }
  }

  it should "map just the default value" in {
    baseExpect.map(firstWord).run() map {
      _ shouldBe firstWord(defaultValue)
    }
  }
  it should "flatMap just the default value" in {
    baseExpect.flatMap(baseExpectWithFunction(firstWord)).run() map {
      _ shouldBe firstWord(defaultValue)
    }
  }
  it should "transform just the default value (map)" in {
    baseExpect.transform(
      PartialFunction.empty,
      { case t => firstWord(t) }
    ).run() map {
      _ shouldBe firstWord(defaultValue)
    }
  }
  it should "transform just the default value (flatmap)" in {
    baseExpect.transform(
      { case t => baseExpectWithFunction(firstWord)(t) },
      PartialFunction.empty
    ).run() map {
      _ shouldBe firstWord(defaultValue)
    }
  }

  "Transforming when the defaultValue is not in domain" should "throw a NoSuchElementException" in {
    val thrown = the [NoSuchElementException] thrownBy baseExpect.transform[String](
      PartialFunction.empty,
      PartialFunction.empty
    )
    thrown.getMessage should include (defaultValue)
  }

  "An Expect with an empty expect block" should "fail with IllegalArgumentException" in {
    assertThrows[IllegalArgumentException] {
      new Expect("ls", defaultValue = ())(
        ExpectBlock()
      )
    }
  }

  "An Expect with an empty when" should "return the default value" in {
    new Expect("echo ola", defaultValue)(
      ExpectBlock(
        When("ola")(
          //Purposefully left empty
        )
      )
    ).run() map {
      _ shouldBe defaultValue
    }
  }
}
