package work.martins.simon.expect.core

import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AsyncFlatSpec
import work.martins.simon.expect.TestUtils

class ReadFromSpec extends AsyncFlatSpec with TestUtils with BeforeAndAfterEach:
  val builder = new StringBuilder("")
  val expectedValue = "ReturnedValue"
  
  override protected def beforeEach(): Unit = builder.clear()