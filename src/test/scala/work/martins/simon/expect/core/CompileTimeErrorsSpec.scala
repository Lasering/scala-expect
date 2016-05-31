package work.martins.simon.expect.core

import org.scalatest.{FlatSpec, Matchers}
import work.martins.simon.expect.TestUtils
import work.martins.simon.expect.core.actions._

class CompileTimeErrorsSpec extends FlatSpec with Matchers with TestUtils {
  "A StringWhen" should "not type check if it contains a SendWithRegex" in {
    //new StringWhen("text")(SendWithRegex(m => m.group(1)))
    """new StringWhen("text")(SendWithRegex(m => m.group(1)))""" shouldNot typeCheck
  }
  it should "not type check if it contains a ReturningWithRegex" in {
    //new StringWhen("text")(ReturningWithRegex(m => m.group(1)))
    """new StringWhen("text")(ReturningWithRegex(m => m.group(1)))""" shouldNot typeCheck
  }
  it should "not type check if it contains a ReturningExpectWithRegex" in {
    //new StringWhen("text")(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))
    """new StringWhen("text")(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))""" shouldNot typeCheck
  }

  "A RegexWhen" should "compile if it contains a SendWithRegex" in {
    //This line of code is here as a fail fast mechanism
    new RegexWhen("text".r)(SendWithRegex(m => m.group(1)))
    """new RegexWhen("text".r)(SendWithRegex(m => m.group(1)))""" should compile
  }
  it should "compile if it contains a ReturningWithRegex" in {
    //This line of code is here as a fail fast mechanism
    new RegexWhen("text".r)(ReturningWithRegex(m => m.group(1)))
    """new RegexWhen("text".r)(ReturningWithRegex(m => m.group(1)))""" should compile
  }
  it should "compile if it contains a ReturningExpectWithRegex" in {
    //This line of code is here as a fail fast mechanism
    new RegexWhen("text".r)(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))
    """new RegexWhen("text".r)(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))""" should compile
  }

  "Actions without regex" should "compile in every When" in {
    //These lines of code are here as a fail fast mechanism
    val actions: Seq[Action[String, When]] = Seq(
      Send(""),
      Returning(""),
      ReturningExpect(new Expect("ls", "")()),
      Exit()
    )
    new StringWhen("")(actions:_*)
    new RegexWhen(".*".r)(actions:_*)
    new EndOfFileWhen(actions:_*)
    new TimeoutWhen(actions:_*)

    """    val actions: Seq[Action[String, When]] = Seq(
      |      Send(""),
      |      Returning(""),
      |      ReturningExpect(new Expect("ls", "")()),
      |      Exit()
      |    )
      |    new StringWhen("")(actions:_*)
      |    new RegexWhen(".*".r)(actions:_*)
      |    new EndOfFileWhen(actions:_*)
      |    new TimeoutWhen(actions:_*)""".stripMargin should compile
  }
}
