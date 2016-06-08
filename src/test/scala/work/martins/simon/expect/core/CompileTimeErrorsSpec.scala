package work.martins.simon.expect.core

import org.scalatest.{FlatSpec, Matchers}
import work.martins.simon.expect.TestUtils
import work.martins.simon.expect.core.actions._

class CompileTimeErrorsSpec extends FlatSpec with Matchers with TestUtils {
  "A StringWhen" should "not type check if it contains a SendWithRegex" in {
    //StringWhen("text")(SendWithRegex(m => m.group(1)))
    """StringWhen("text")(SendWithRegex(m => m.group(1)))""" shouldNot typeCheck
  }
  it should "not type check if it contains a ReturningWithRegex" in {
    //StringWhen("text")(ReturningWithRegex(m => m.group(1)))
    """StringWhen("text")(ReturningWithRegex(m => m.group(1)))""" shouldNot typeCheck
  }
  it should "not type check if it contains a ReturningExpectWithRegex" in {
    //StringWhen("text")(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))
    """StringWhen("text")(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))""" shouldNot typeCheck
  }

  "A RegexWhen" should "compile if it contains a SendWithRegex" in {
    //This line of code is here as a fail fast mechanism
    RegexWhen("text".r)(SendWithRegex(m => m.group(1)))
    """RegexWhen("text".r)(SendWithRegex(m => m.group(1)))""" should compile
  }
  it should "compile if it contains a ReturningWithRegex" in {
    //This line of code is here as a fail fast mechanism
    RegexWhen("text".r)(ReturningWithRegex(m => m.group(1)))
    """RegexWhen("text".r)(ReturningWithRegex(m => m.group(1)))""" should compile
  }
  it should "compile if it contains a ReturningExpectWithRegex" in {
    //This line of code is here as a fail fast mechanism
    RegexWhen("text".r)(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))
    """RegexWhen("text".r)(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))""" should compile
  }

  "Actions without regex" should "compile in every When" in {
    //These lines of code are here as a fail fast mechanism
    val actions: Seq[Action[String, When]] = Seq(
      Send(""),
      Returning(""),
      ReturningExpect(new Expect("ls", "")()),
      Exit()
    )
    StringWhen("")(actions:_*)
    RegexWhen(".*".r)(actions:_*)
    EndOfFileWhen(actions:_*)
    TimeoutWhen(actions:_*)

    """val actions: Seq[Action[String, When]] = Seq(
         Send(""),
         Returning(""),
         ReturningExpect(new Expect("ls", "")()),
         Exit()
       )
       StringWhen("")(actions:_*)
       RegexWhen(".*".r)(actions:_*)
       EndOfFileWhen(actions:_*)
       TimeoutWhen(actions:_*)""" should compile
  }
}
