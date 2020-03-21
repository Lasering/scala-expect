package work.martins.simon.expect.core

import org.scalatest.BeforeAndAfterEach
import work.martins.simon.expect.TestUtils
import org.scalatest.flatspec.AsyncFlatSpec

/**
  * Created by simon on 19-01-2017.
  */
class ReadFromSpec extends AsyncFlatSpec with TestUtils with BeforeAndAfterEach {
  val builder = new StringBuilder("")
  val expectedValue = "ReturnedValue"
  
  override protected def beforeEach(): Unit = builder.clear()
  
  
  
}
