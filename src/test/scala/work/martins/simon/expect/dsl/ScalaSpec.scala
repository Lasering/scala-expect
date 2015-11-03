package work.martins.simon.expect.dsl

object ScalaSpec extends App {
  val e = new Expect("scala", defaultValue = 5) {
    expect("scala>") {
      sendln("1 + 2")
      returning(_.group(1).toInt)
    }
    expect( """res0: Int = (\d+)""".r) {
      returning(_.group(1).toInt)
    }
  }
}
