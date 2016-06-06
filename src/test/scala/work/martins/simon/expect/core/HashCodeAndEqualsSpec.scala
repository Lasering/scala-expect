package work.martins.simon.expect.core

import org.scalacheck.Arbitrary._
import org.scalatest.{Matchers, PropSpecLike}
import work.martins.simon.expect.TestUtils


class HashCodeAndEqualsSpec extends PropSpecLike with Matchers with TestUtils with Generators {
  property("hashCode and equals should work when the expect is the same") {
    forAll(genSingleExpectBlockWhenMultipleActionExpect[String]) { case (expect, _, _, _) =>
      Set(expect, expect) should contain only expect
    }
  }

  property("hashCode and equals should work when the expect is NOT the same") {
    forAll(genSingleExpectBlockWhenMultipleActionExpect[String],
           genSingleExpectBlockWhenMultipleActionExpect[String]) { case ((expect1, _, _, _), (expect2, _, _, _)) =>
      Set(expect1, expect2) should contain allOf (expect1, expect2)
    }
  }
}
