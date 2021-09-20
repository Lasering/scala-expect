package work.martins.simon.expect.dsl

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*
import work.martins.simon.expect.*
import work.martins.simon.expect.FromInputStream.*
import work.martins.simon.expect.fluent.{Expect => _, *}

class SyntaxSpec extends AnyFlatSpec with Matchers:
  "An Expect being constructed illegally" should "throw IllegalArgumentException" in {
    //Without a command
    an [IllegalArgumentException] should be thrownBy new Expect("", defaultValue = ())
    
    new Expect("ls", "") { self =>
      //Empty expect block
      an [IllegalArgumentException] should be thrownBy expectBlock {}
      
      expectBlock {
        //Adding expectBlock inside expectBlock
        "expectBlock {}" shouldNot compile
        // Prevents error "Expect block cannot be empty."
        when(""){}
      }
    }
  }
  "An Except being constructed legally" should "not throw IllegalArgumentException" in {
    def addExpectBlocks(e: Expect[String]): Unit =
      import e.*
      expectBlock {
        addWhens(addMultipleWhens)
      }
      expectBlock {
        when("") {
          addActions(addWhenActions)
        }
      }
      expectBlock {
        when("".r) {
          addActions(addWhenActions)
          addActions(addRegexWhenActions)
        }
      }
      expectBlock {
        when(EndOfFile) {
          addActions(addWhenActions)
        }
      }
      expectBlock {
        when(Timeout) {
          addActions(addWhenActions)
        }
      }
    
    def addMultipleWhens(e: Expect[String])(using ExpectBlock[String]): Unit =
      import e.*
      when("", StdErr){
        addActions(addWhenActions)
      }
      when("".r, StdOut){
        addActions(addWhenActions)
        addActions(addRegexWhenActions)
      }
      when(EndOfFile, StdErr){
        addActions(addWhenActions)
      }
      when(Timeout){
        addActions(addWhenActions)
      }
    
    def addWhenActions(e: Expect[String])(using When[String]): Unit =
      import e.*
      send("")
      sendln("")
      returning("")
      returningExpect(new Expect("ls", "", Settings.fromConfig()))
      exit()
    
    def addRegexWhenActions(e: Expect[String])(using RegexWhen[String]): Unit =
      import e.*
      send(_ => "")
      sendln(_ => "")
      returning(_ => "")
      returningExpect(_ => new Expect(Seq("ls"), "", Settings()))
    
    def addEndOfFileWhen(e: Expect[String])(using ExpectBlock[String]): Unit =
      import e.*
      when(EndOfFile) {
        send("")
      }
    
    noException should be thrownBy {
      new Expect("ls", "") {
        addExpectBlock(addExpectBlocks)
        expectBlock {
          addWhen(addEndOfFileWhen)
        }
      }
    }
  }
