package work.martins.simon.expect.core

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.*
import work.martins.simon.expect.core.actions.*
import work.martins.simon.expect.{EndOfFile, Timeout}

class StructuralEqualitySpec extends AnyFlatSpecLike with Matchers:
  val actions = List(
    Exit(),
    Send("test"),
    Sendln(m => s"${m.group(1)}"),
    Returning(5),
    Returning(m => m.group(1).toInt),
    ReturningExpect(new Expect("ls", 6)()),
    ReturningExpect(m => new Expect("ls", m.group(1).toInt)()),
  )
  actions.foreach { action =>
    s"${action.getClass.getSimpleName}" should "structurallyEqual itself" in {
      action.structurallyEquals(action) shouldBe true
    }
    it should "not structurallyEqual any other action" in {
      actions.filter(_ != action).foreach { other =>
        action.structurallyEquals(other) shouldBe false
      }
    }
  }
  
  val whens = List(
    When("test")(),
    When("test".r)(),
    When(Timeout)(),
    When(EndOfFile)(),
  )
  whens.foreach { when =>
    s"${when.getClass.getSimpleName}" should "structurallyEqual itself" in {
      when.structurallyEquals(when) shouldBe true
    }
    it should "not structurallyEqual any other whens" in {
      whens.filter(_ != when).foreach { other =>
        when.structurallyEquals(other) shouldBe false
      }
    }
  }
  
  // ExpectBlocks and Expects are being tested under ToCoreSpec no need to repeat ourselves