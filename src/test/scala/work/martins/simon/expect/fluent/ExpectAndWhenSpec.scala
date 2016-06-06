package work.martins.simon.expect.fluent

import org.scalatest.{FreeSpec, Matchers}
import work.martins.simon.expect.{EndOfFile, Timeout, core}

//The execution of a fluent.Expect is delegated to core.Expect
//This means fluent.Expect does not know how to execute Expects.
//So there isn't a need to test execution of Expects in the fluent package.
//There is, however, the need to test that the core.Expect generated from a fluent.Expect is the correct one.

class ExpectAndWhenSpec extends FreeSpec with Matchers {
  "An Expect" - {
    "should generate the correct core.Expect when" - {
      val coreExpect = new core.Expect("ls", defaultValue = ())(
        core.ExpectBlock(
          core.StringWhen("1")(),
          core.RegexWhen("2".r)(),
          core.EndOfFileWhen(),
          core.TimeoutWhen()
        ),
        core.ExpectBlock(
          core.StringWhen("1")(),
          core.RegexWhen("2".r)(),
          core.EndOfFileWhen()
        ),
        core.ExpectBlock(
          core.EndOfFileWhen(),
          core.TimeoutWhen()
        ),
        core.ExpectBlock(
          core.TimeoutWhen()
        )
      )

      "using the verbose way" in {
        val fe = new Expect("ls", defaultValue = ())
        fe.expect
          .when("1")
          .when("2".r)
          .when(EndOfFile)
          .when(Timeout)
        fe.expect
          .when("1")
          .when("2".r)
          .when(EndOfFile)
        fe.expect
          .when(EndOfFile)
          .when(Timeout)
        fe.expect
          .when(Timeout)

        fe.toCore shouldEqual coreExpect
      }

      def addOneWhen(e: ExpectBlock[Unit]): StringWhen[Unit] = e.when("1")

      "using shortcuts and addWhen" in {
        val fe = new Expect("ls", defaultValue = ())
        fe.expect
          .addWhen(addOneWhen)
          .when("2".r)
          .when(EndOfFile)
          .when(Timeout)
        fe.expect
          .addWhen(addOneWhen)
          .when("2".r)
          .when(EndOfFile)
        //This is very confusing. Not recommended.
        fe.expect(EndOfFile)
          .when(Timeout)
        fe.expect(Timeout)

        fe.toCore shouldEqual coreExpect
      }
      "using addExpectBlock and addWhens" in {
        def addItBlock(e: Expect[Unit]): Unit = {
          e.expect
            .addWhen(addOneWhen)
            .when("2".r)
            .addWhens(addSomeWhens)
        }

        def addSomeWhens(eb: ExpectBlock[Unit]): Unit = {
          eb
            .when(EndOfFile)
            .when(Timeout)
        }

        val fe = new Expect("ls", defaultValue = ())
        fe.addExpectBlock(addItBlock)
        //Although this is possible it is not recommended because its harder to read.
        //The addExpectBlock should be used to refactor out common expect blocks
        //and therefor should be used like the previous line.
        fe.addExpectBlock { e =>
          e.expect
            .addWhen(addOneWhen)
            .when("2".r)
            .when(EndOfFile)
        }
        fe.expect
          .addWhens(addSomeWhens)
        //Just like addExpectBlock this syntax is discouraged.
        //In this case it is even more discouraged because we are just adding one when.
        //The more suited alternative would be using addWhen(functionThatAddsIt)
        fe.expect
          .addWhens{ eb =>
            eb.when(Timeout)
          }

        fe.toCore shouldEqual coreExpect
      }
    }
  }
}
