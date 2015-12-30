package work.martins.simon.expect.fluent

import scala.util.matching.Regex.Match

class ScalaSpec {
  //The execution of a fluent.Expect is delegated to core.Expect
  //This means fluent.Expect does not know how to execute Expects.
  //So there isn't a need to test execution of Expects in the fluent package.
  //There is, however, the need to test that the core.Expect generated from a fluent.Expect is the correct one.

  val e = new Expect("bc -i", defaultValue = 5)
    .expect("For details type `warranty'.")
      .sendln("1 + 2")
    .expect("""(\d+)""".r)
      .returning{ m: Match =>
        println("Blas")
        m.group(1).toInt
      }
}
