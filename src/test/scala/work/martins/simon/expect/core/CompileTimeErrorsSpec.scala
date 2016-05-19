package work.martins.simon.expect.core

import org.scalatest.{FlatSpec, Matchers}
import work.martins.simon.expect.TestUtils

class CompileTimeErrorsSpec extends FlatSpec with Matchers with TestUtils {
  "A StringWhen" should "not compile if it contains a SendWithRegex" in {
    //new StringWhen("text")(SendWithRegex(m => m.group(1)))
    """new StringWhen("text")(SendWithRegex(m => m.group(1)))""" shouldNot typeCheck
  }
  it should "not compile if it contains a ReturningWithRegex" in {
    //new StringWhen("text")(ReturningWithRegex(m => m.group(1)))
    """new StringWhen("text")(ReturningWithRegex(m => m.group(1)))""" shouldNot typeCheck
  }
  it should "not compile if it contains a ReturningExpectWithRegex" in {
    //new StringWhen("text")(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))
    """new StringWhen("text")(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))""" shouldNot typeCheck
  }

  "A RegexWhen" should "compile if it contains a SendWithRegex" in {
    new RegexWhen("text".r)(SendWithRegex(m => m.group(1)))
    """new RegexWhen("text".r)(SendWithRegex(m => m.group(1)))""" should compile
  }
  it should "compile if it contains a ReturningWithRegex" in {
    new RegexWhen("text".r)(ReturningWithRegex(m => m.group(1)))
    """new RegexWhen("text".r)(ReturningWithRegex(m => m.group(1)))""" should compile
  }
  it should "compile if it contains a ReturningExpectWithRegex" in {
    new RegexWhen("text".r)(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))
    """new RegexWhen("text".r)(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))""" should compile
  }

  "Actions without regex" should "compile in every When" in {
    val sw = new StringWhen("")(
      Send(""),
      Returning(() => ""),
      ReturningExpect(() => new Expect("ls", "")()),
      Exit()
    )
    new RegexWhen(".*".r)(
      Send(""),
      Returning(() => ""),
      ReturningExpect(() => new Expect("ls", "")()),
      Exit()
    )
    new EndOfFileWhen(
      Send(""),
      Returning(() => ""),
      ReturningExpect(() => new Expect("ls", "")()),
      Exit()
    )
    new TimeoutWhen(
      Send(""),
      Returning(() => ""),
      ReturningExpect(() => new Expect("ls", "")()),
      Exit()
    )

    """new StringWhen("")(
      |  Send(""),
      |  Returning(() => ""),
      |  ReturningExpect(() => new Expect("ls", "")()),
      |  Exit()
      |)
      |new RegexWhen(".*".r)(
      |  Send(""),
      |  Returning(() => ""),
      |  ReturningExpect(() => new Expect("ls", "")()),
      |  Exit()
      |)
      |new EndOfFileWhen(
      |  Send(""),
      |  Returning(() => ""),
      |  ReturningExpect(() => new Expect("ls", "")()),
      |  Exit()
      |)
      |new TimeoutWhen(
      |  Send(""),
      |  Returning(() => ""),
      |  ReturningExpect(() => new Expect("ls", "")()),
      |  Exit()
      |)""".stripMargin should compile
  }
}
