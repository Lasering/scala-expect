package work.martins.simon.expect.dsl

import work.martins.simon.expect._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SyntaxSpec extends AnyFlatSpec with Matchers {
  def illegalExpectBlocks(e: Expect[String]): Unit = {
    import e._
    an [IllegalArgumentException] should be thrownBy expect{}
    an [IllegalArgumentException] should be thrownBy expect{ when(""){} }
    an [IllegalArgumentException] should be thrownBy expect{ when("", StdErr){} }
    an [IllegalArgumentException] should be thrownBy expect{ when("".r){} }
    an [IllegalArgumentException] should be thrownBy expect{ when("".r, StdErr){} }
    an [IllegalArgumentException] should be thrownBy expect{ when(EndOfFile){} }
    an [IllegalArgumentException] should be thrownBy expect{ when(EndOfFile, StdErr){} }
    an [IllegalArgumentException] should be thrownBy expect{ when(Timeout){} }
    ()
  }
  def illegalWhens(e: Expect[String]): Unit = {
    import e._
    an [IllegalArgumentException] should be thrownBy when(""){}
    an [IllegalArgumentException] should be thrownBy when("", StdOut){}
    an [IllegalArgumentException] should be thrownBy when("".r){}
    an [IllegalArgumentException] should be thrownBy when("".r, StdOut){}
    an [IllegalArgumentException] should be thrownBy when(EndOfFile){}
    an [IllegalArgumentException] should be thrownBy when(EndOfFile, StdOut){}
    an [IllegalArgumentException] should be thrownBy when(Timeout){}
    ()
  }
  def illegalWhenActions(e: Expect[String]): Unit = {
    import e._
    an [IllegalArgumentException] should be thrownBy send("")
    an [IllegalArgumentException] should be thrownBy sendln("")
    an [IllegalArgumentException] should be thrownBy returning("")
    an [IllegalArgumentException] should be thrownBy returningExpect(new Expect("ls", "", Settings.fromConfig()))
    an [IllegalArgumentException] should be thrownBy exit()
    ()
  }
  def illegalRegexWhenActions(e: Expect[String]): Unit = {
    import e._
    an [IllegalArgumentException] should be thrownBy send(_ => "")
    an [IllegalArgumentException] should be thrownBy sendln(_ => "")
    an [IllegalArgumentException] should be thrownBy returning(_ => "")
    an [IllegalArgumentException] should be thrownBy returningExpect(_ => Expect(Seq("ls"), "", Settings()))
    ()
  }

  "An Expect being constructed illegally" should "throw IllegalArgumentException" in {
    //Without a command
    an [IllegalArgumentException] should be thrownBy new Expect("", defaultValue = ())

    new Expect("ls", "") { self =>
      //Empty expect block
      an [IllegalArgumentException] should be thrownBy expect {}

      expect {
        //Adding expect block inside expect block
        addExpectBlock(illegalExpectBlocks)

        //an action directly to the body of an expect block
        addActions(illegalWhenActions)
        addActions(illegalRegexWhenActions)

        when("") {
          //Adding expect block inside when
          addExpectBlock(illegalExpectBlocks)

          //Adding when inside when
          addWhens(illegalWhens)

          //Adding regex actions inside a string when
          addActions(illegalRegexWhenActions)
        }
      }

      //Adding a when directly to the body of the expect
      addWhens(illegalWhens)

      //Adding an action directly to the body of the expect
      addActions(illegalWhenActions)
      addActions(illegalRegexWhenActions)
    }
  }

  "An Except being constructed legally" should "not throw IllegalArgumentException" in {
    def addExpectBlocks(e: Expect[String]): Unit = {
      import e._
      e.expect {
        addWhens(addMultipleWhens)
      }
      e.expect{
        when("") {
          addActions(addWhenActions)
        }
      }
      e.expect{
        when("".r) {
          addActions(addWhenActions)
          addActions(addRegexWhenActions)
        }
      }
      e.expect{
        when(EndOfFile) {
          addActions(addWhenActions)
        }
      }
      e.expect{
        when(Timeout) {
          addActions(addWhenActions)
        }
      }
    }
    def addMultipleWhens(e: Expect[String]): Unit = {
      import e._
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
    }
    def addWhenActions(e: Expect[String]): Unit = {
      import e._
      send("")
      sendln("")
      returning("")
      returningExpect(new Expect("ls", "", Settings.fromConfig()))
      exit()
    }
    def addRegexWhenActions(e: Expect[String]): Unit = {
      import e._
      send(_ => "")
      sendln(_ => "")
      returning(_ => "")
      returningExpect(_ => new Expect(Seq("ls"), "", Settings()))
    }
    def addEndOfFileWhen(e: Expect[String]): Unit = {
      import e._
      when(EndOfFile) {
        send("")
      }
    }

    noException should be thrownBy {
      new Expect("ls", "") {
        addExpectBlock(addExpectBlocks)

        expect {
          addWhen(addEndOfFileWhen)
        }
      }
    }
  }
}
