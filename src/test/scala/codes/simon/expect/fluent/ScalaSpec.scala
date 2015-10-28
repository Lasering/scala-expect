package codes.simon.expect.fluent

import scala.util.matching.Regex.Match

class ScalaSpec {
    val e = new Expect("scala", 5)
      .expect("scala>")
        .sendln("1 + 2")
      .expect("""res0: Int = (\d+)""".r)
        .returning{ m: Match =>
          println("Blas")
          m.group(1).toInt
        }
}
