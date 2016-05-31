package work.martins.simon.expect.core

import org.scalacheck.Arbitrary._
import org.scalatest.{Matchers, PropSpecLike}
import work.martins.simon.expect.TestUtils
import work.martins.simon.expect.core.actions._

import scala.annotation.tailrec

class MapAndFlatMapSpec extends PropSpecLike with Matchers with TestUtils with Generators {
  def numberOfReturningsToAnExit[R](expect: Expect[R]): Int = {
    expect.expectBlocks.flatMap(_.whens.map { when =>
      @tailrec
      def countReturnings(actions: Seq[Action[R, when.This]], count: Int): Int = actions match {
        case Nil => count
        case (_: Exit[R]) :: tail => count
        case (_: ReturningExpect[R] | _: ReturningExpectWithRegex[R]) :: tail => count + 1
        case (_: Returning[R] | _: ReturningWithRegex[R]) :: tail => countReturnings(tail, count + 1)
        case _ :: tail => countReturnings(tail, count)
      }
      countReturnings(when.actions, 0)
    }).headOption.getOrElse(0)
  }

  def numberOfDigits(s: String): Int = (s * 2).count(_.isDigit)

  property("Mapping: must not cause the actions to be executed and must return the mapped result") {
    forAll(genSingleExpectBlockWhenMultipleActionExpect[String]) { case (expect, builder, addToBuilder, result) =>
      val mappedExpect = expect.map(numberOfDigits)

      //Ensure the actions were not executed in the map
      builder.result() shouldBe empty
      //Ensure the defaultValue was mapped
      mappedExpect.defaultValue shouldBe numberOfDigits(expect.defaultValue)

      val numberOfReturnings = numberOfReturningsToAnExit(mappedExpect)
      mappedExpect.whenReady { obtainedResult =>
        if (numberOfReturnings == 0) {
          //If there were no returnings then the default value will be returned
          obtainedResult shouldBe numberOfDigits(expect.defaultValue)
        } else {
          builder.result() shouldBe (addToBuilder * numberOfReturnings)
          obtainedResult shouldBe numberOfDigits(result)
        }
      }
    }
  }

  property("FlatMapping: must not cause the actions to be executed and must return the flatMapped result") {
    forAll(genSingleExpectBlockWhenMultipleActionExpect[String]) { case (expect, builder, addToBuilder, result) =>
      def flatMap(s: String): Expect[Int] = new Expect("ls", numberOfDigits(s))()

      val flatMappedExpect = expect.flatMap(flatMap)

      //Ensure the actions were not executed in the flatMap
      builder.result() shouldBe empty
      //Ensure the defaultValue was flatMapped
      flatMappedExpect.defaultValue shouldBe numberOfDigits(expect.defaultValue)

      val numberOfReturnings = numberOfReturningsToAnExit(flatMappedExpect)
      flatMappedExpect.whenReady { obtainedResult =>
        if (numberOfReturnings == 0) {
          //If there were no returnings then the default value will be returned
          obtainedResult shouldBe numberOfDigits(expect.defaultValue)
        } else {
          builder.result() shouldBe (addToBuilder * numberOfReturnings)
          obtainedResult shouldBe numberOfDigits(result)
        }
      }
    }
  }
}
