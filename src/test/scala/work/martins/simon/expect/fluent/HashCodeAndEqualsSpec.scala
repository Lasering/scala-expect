package work.martins.simon.expect.fluent

import org.scalatest.{FlatSpec, Matchers}
import work.martins.simon.expect.{EndOfFile, TestUtils, Timeout}


class HashCodeAndEqualsSpec extends FlatSpec with Matchers with TestUtils {
  val expect1 = new Expect("ls", "") {
    expect
      .when("1")
      .when("2".r)
        .send(m => s"Hey this is not the same!")
      .when(EndOfFile)
      .when(Timeout)
  }

  "hashCode and equals" should "work when the expect is the same" in {
    Set(expect1, expect1) should contain only expect1
  }

  "hashCode and equals" should "work when the expect is NOT the same" in {
    val expect2 = new Expect("ls", "") {
      expect("2".r)
    }
    val expect3 = new Expect("ls", "") {
      expect(EndOfFile)
    }
    val expect4 = new Expect("ls", "") {
      expect(Timeout)
    }
    Set(expect1, expect2, expect3, expect4) should contain allOf (expect1, expect2, expect3, expect4)
  }
}
