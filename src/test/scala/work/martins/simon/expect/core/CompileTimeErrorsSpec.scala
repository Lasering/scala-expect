package work.martins.simon.expect.core

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.*
import work.martins.simon.expect.core.actions.*
import work.martins.simon.expect.{EndOfFile, Timeout}

class CompileTimeErrorsSpec extends AnyFlatSpecLike with Matchers:
  "A StringWhen" should "not type check if it contains a SendWithRegex" in {
    //When("text")(Send(m => m.group(1)))
    """When("text")(Send(m => m.group(1)))""" shouldNot typeCheck
  }
  it should "not type check if it contains a ReturningWithRegex" in {
    //When("text")(Returning(m => m.group(1)))
    """When("text")(Returning(m => m.group(1)))""" shouldNot typeCheck
  }
  it should "not type check if it contains a ReturningExpectWithRegex" in {
    //When("text")(ReturningExpect(m => new Expect(m.group(1), "")()))
    """When("text")(ReturningExpect(m => new Expect(m.group(1), "")()))""" shouldNot typeCheck
  }
  
  "A RegexWhen" should "compile if it contains a SendWithRegex" in {
    //This line is a fail fast mechanism
    When("text".r)(Send(m => m.group(1)))
    """When("text".r)(Send(m => m.group(1)))""" should compile
  }
  it should "compile if it contains a ReturningWithRegex" in {
    //This line is a fail fast mechanism
    When("text".r)(Returning(m => m.group(1)))
    """When("text".r)(Returning(m => m.group(1)))""" should compile
  }
  it should "compile if it contains a ReturningExpectWithRegex" in {
    //This line is a fail fast mechanism
    When("text".r)(ReturningExpect(m => new Expect(m.group(1), "")()))
    """When("text".r)(ReturningExpect(m => new Expect(m.group(1), "")()))""" should compile
  }
  
  "Actions without regex" should "compile in every When" in {
    //These lines are a fail fast mechanism
    val actions: Seq[Action[String, When]] = Seq(
      Send(""),
      Returning(""),
      ReturningExpect(new Expect("ls", "")()),
      Exit()
    )
    When("")(actions*)
    When(".*".r)(actions*)
    When(EndOfFile)(actions*)
    When(Timeout)(actions*)
    
    """val actions: Seq[Action[String, When]] = Seq(
         Send(""),
         Returning(""),
         ReturningExpect(new Expect("ls", "")()),
         Exit()
       )
       When("")(actions*)
       When(".*".r)(actions*)
       When(EndOfFile)(actions*)
       When(Timeout)(actions*)""" should compile
  }
