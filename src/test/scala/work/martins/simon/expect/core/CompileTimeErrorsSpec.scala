package work.martins.simon.expect.core

import work.martins.simon.expect.core.actions._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CompileTimeErrorsSpec extends AnyFlatSpec with Matchers {
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
    //This line is a fail fast mechanism
    RegexWhen("text".r)(SendWithRegex(m => m.group(1)))
    """RegexWhen("text".r)(SendWithRegex(m => m.group(1)))""" should compile
  }
  it should "compile if it contains a ReturningWithRegex" in {
    //This line is a fail fast mechanism
    RegexWhen("text".r)(ReturningWithRegex(m => m.group(1)))
    """RegexWhen("text".r)(ReturningWithRegex(m => m.group(1)))""" should compile
  }
  it should "compile if it contains a ReturningExpectWithRegex" in {
    //This line is a fail fast mechanism
    RegexWhen("text".r)(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))
    """RegexWhen("text".r)(ReturningExpectWithRegex(m => new Expect(m.group(1), "")()))""" should compile
  }

  "Actions without regex" should "compile in every When" in {
    //These lines are a fail fast mechanism
    val actions: Seq[Action[String, When]] = Seq(
      Send(""),
      Returning(""),
      ReturningExpect(new Expect("ls", "")()),
      Exit()
    )
    StringWhen("")(actions:_*)
    RegexWhen(".*".r)(actions:_*)
    EndOfFileWhen()(actions:_*)
    TimeoutWhen()(actions:_*)
    
    """val actions: Seq[Action[String, When]] = Seq(
         Send(""),
         Returning(""),
         ReturningExpect(new Expect("ls", "")()),
         Exit()
       )
       StringWhen("")(actions:_*)
       RegexWhen(".*".r)(actions:_*)
       EndOfFileWhen()(actions:_*)
       TimeoutWhen()(actions:_*)""" should compile
  }
}
