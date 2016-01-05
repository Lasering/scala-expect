package work.martins.simon.expect.fluent

import org.scalatest.{FreeSpec, Matchers}
import work.martins.simon.expect.core

class ExpectAndWhenSpec extends FreeSpec with Matchers {
  "An Expect should generate the correct core.Expect when" - {
    "multiple expect blocks are added" - {
      val coreExpect = new core.Expect("ls", defaultValue = ())(
        new core.ExpectBlock(
          new core.StringWhen("1")()
        ),
        new core.ExpectBlock(
          new core.StringWhen("2")()
        ),
        new core.ExpectBlock(
          new core.StringWhen("3")()
        )
      )

      def addTwoExpectBlock(e: Expect[Unit]): Unit = e.expect("2")

      "using the verbose way" in {
        val fe = new Expect("ls", defaultValue = ())
        fe.expect
          .when("1")
        fe.expect
          .when("2")
        fe.expect
          .when("3")
        fe.toCore shouldEqual coreExpect
      }
      "using shortcuts" in {
        val fe = new Expect("ls", defaultValue = ())
        fe.expect("1")
        fe.expect("2")
        fe.expect("3")
        fe.toCore shouldEqual coreExpect
      }
      "using addExpectBlock" in {
        val fe = new Expect("ls", defaultValue = ())
        fe.addExpectBlock { e =>
          e.expect("1")
        }
        fe.addExpectBlock(addTwoExpectBlock)
        fe.addExpectBlock(_.expect("3"))
        fe.toCore shouldEqual coreExpect
      }
      "mixing all the alternatives" in {
        val fe = new Expect("ls", defaultValue = ())
        fe.expect("1")
        fe.addExpectBlock(addTwoExpectBlock)
        fe.expect
          .when("3")

        fe.toCore shouldEqual coreExpect
      }
    }
    "multiple whens are added" - {
      val coreExpect = new core.Expect("ls", defaultValue = ())(
        new core.ExpectBlock(
          new core.StringWhen("1")(),
          new core.StringWhen("2")(),
          new core.StringWhen("3")()
        ),
        new core.ExpectBlock(
          new core.StringWhen("1")(),
          new core.StringWhen("2")()
        ),
        new core.ExpectBlock(
          new core.StringWhen("3")()
        )
      )

      def addIWhen(i: Int)(e: ExpectBlock[Unit]) = e.when(s"$i")

      "using the verbose way" in {
        val fe = new Expect("ls", defaultValue = ())
        fe.expect
          .when("1")
          .when("2")
          .when("3")
        fe.expect
          .when("1")
          .when("2")
        fe.expect
          .when("3")
        fe.toCore shouldEqual coreExpect
      }
      "using addWhen" in {
        val fe = new Expect("ls", defaultValue = ())
        fe.expect
          .addWhen(addIWhen(1))
          .when("2")
          .when("3")
        fe.expect
          .when("1")
          .addWhen(addIWhen(2))
        fe.expect
          .when("3")
        fe.toCore shouldEqual coreExpect
      }
      "using addWhens" in {
        val fe = new Expect("ls", defaultValue = ())
        fe.expect
          .addWhens{ eb =>
            eb.when("1")
            eb.when("2")
            eb.when("3")
          }
        fe.expect
          .addWhens{ eb =>
            eb.when("1")
            eb.when("2")
          }
        //This should be avoided because we are just adding a single when.
        //However given the more relaxed type of f there is legitimate uses to do something like this.
        //But these uses would most probably use a function and won't use a lambda directly.
        fe.expect
          .addWhens(_.when("3"))

        fe.toCore shouldEqual coreExpect
      }
      "mixing all the alternatives" in {
        val fe = new Expect("ls", defaultValue = ())
        fe.expect
          .when("1")
          .when("2")
          .when("3")
        fe.expect
          .addWhens{ eb =>
            eb.when("1")
            eb.when("2")
          }
        fe.expect
          .addWhen(addIWhen(3))

        fe.toCore shouldEqual coreExpect
      }
    }
  }
}
