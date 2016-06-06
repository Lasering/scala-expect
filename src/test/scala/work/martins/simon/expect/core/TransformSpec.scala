package work.martins.simon.expect.core

import com.typesafe.scalalogging.LazyLogging
import org.scalacheck.Arbitrary._
import org.scalatest.{Matchers, PropSpecLike}
import work.martins.simon.expect.TestUtils


class TransformSpec extends PropSpecLike with Matchers with TestUtils with Generators with LazyLogging {
  def numberOfDigits(s: String): Int = (s * 2).count(_.isDigit)

  //implicit override val generatorDrivenConfig = PropertyCheckConfig(minSuccessful = 100, workers = 8, maxDiscarded = 1000)

  property("Transform: mapping the default value, flatMapping the result - must not cause the actions to be executed") {
    forAll(genSingleExpectBlockWhenMultipleActionExpect[String],
           genSingleExpectBlockWhenMultipleActionExpect[Int], arbitrary[Int]) { case (outer, inner, newDefaultvalue) =>
      val (outerExpect, outerBuilder, _, outerResult) = outer
      val (innerExpect, innerBuilder, _, _) = inner

      val transformedExpect = outerExpect.transform {
        case `outerResult` => innerExpect
      } {
        case outerExpect.defaultValue => newDefaultvalue
      }

      //Ensure the actions were not executed in the transform
      outerBuilder.result() shouldBe empty
      innerBuilder.result() shouldBe empty
      //Ensure the defaultValue was transformed (is this case mapped)
      transformedExpect.defaultValue shouldBe newDefaultvalue
    }
  }

  property("Transform: flatMapping the default value, mapping the result - must not cause the actions to be executed") {
    forAll(genSingleExpectBlockWhenMultipleActionExpect[String],
      genSingleExpectBlockWhenMultipleActionExpect[Int], arbitrary[Int]) { case (outer, inner, newValue) =>
      val (outerExpect, outerBuilder, _, outerResult) = outer
      val (innerExpect, innerBuilder, _, _) = inner

      val transformedExpect = outerExpect.transform {
        case outerExpect.defaultValue => innerExpect
      } {
        case `outerResult` => newValue
      }

      //Ensure the actions were not executed in the transform
      outerBuilder.result() shouldBe empty
      innerBuilder.result() shouldBe empty
      //Ensure the defaultValue was transformed (is this case flatMapped)
      transformedExpect.defaultValue shouldBe innerExpect.defaultValue
    }
  }

  property("Transform: mapping the default value, flatMapping the result - must return the correct result") {
    forAll(genSingleExpectBlockWhenMultipleActionExpect[String],
           genSingleExpectBlockWhenMultipleActionExpect[Int], arbitrary[Int]) { case (outer, inner, newDefaultvalue) =>
      val (outerExpect, outerBuilder, outerAddToBuilder, outerResult) = outer
      val (innerExpect, innerBuilder, innerAddToBuilder, innerResult) = inner

      //We need to compute this before the transform because it will change
      //Returning and ReturningWithRegex to an ActionReturningAction.
      val outerReturnings = numberOfReturningsToAnExit(outerExpect)
      val innerReturnings = numberOfReturningsToAnExit(innerExpect)

      val transformedExpect = outerExpect.transform {
        case `outerResult` => innerExpect
      }{
        case outerExpect.defaultValue => newDefaultvalue
      }

      transformedExpect.whenReady { obtainedResult =>
        if (outerReturnings == 0) {
          //The outer expect did not contain any returning.
          //So the only thing the transform did was map the defaultValue
          obtainedResult shouldBe newDefaultvalue
          //The innerExpect never executed, so its actions must not have been executed
          innerBuilder.result() shouldBe empty
          //The outerExpect had no returnings so the builder must be empty
          outerBuilder.result() shouldBe empty
        } else {
          //The outer expect contains at least one returning action.
          //Because we flatMapped the outerExpect result we know that:
          // · the first Returning action will be transformed to a ReturningExpect (which contains an implicit Exit)
          // · the first ReturningExpect will remain a ReturningExpect (which contains an implicit exit)
          //So the outerBuilder must have a single value because any action following the first
          //returning won't be ran (because of the implicit Exits)
          outerBuilder.result() shouldBe outerAddToBuilder

          if (innerReturnings == 0) {
            innerBuilder.result() shouldBe empty
            obtainedResult shouldBe innerExpect.defaultValue
          } else {
            innerBuilder.result() shouldBe (innerAddToBuilder * innerReturnings)
            obtainedResult shouldBe innerResult
          }
        }
      }
    }
  }

  //TODO: Transform: flatMapping the default value, mapping the result - must return the correct result

  //TODO: a double transform
}
