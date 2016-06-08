package work.martins.simon.expect.dsl

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import work.martins.simon.expect.{EndOfFile, Timeout}

class SyntaxSpec extends FlatSpec with Matchers {
  def illegalExpectBlocks(e: Expect[String]): Unit = {
    an [IllegalArgumentException] should be thrownBy e.expect{}
    an [IllegalArgumentException] should be thrownBy e.expect(""){}
    an [IllegalArgumentException] should be thrownBy e.expect("".r){}
    an [IllegalArgumentException] should be thrownBy e.expect(EndOfFile){}
    an [IllegalArgumentException] should be thrownBy e.expect(Timeout){}
  }
  def illegalWhens(e: Expect[String]): Unit = {
    import e._
    an [IllegalArgumentException] should be thrownBy when(""){}
    an [IllegalArgumentException] should be thrownBy when("".r){}
    an [IllegalArgumentException] should be thrownBy when(EndOfFile){}
    an [IllegalArgumentException] should be thrownBy when(Timeout){}
  }
  def illegalWhenActions(e: Expect[String]): Unit = {
    import e._
    an [IllegalArgumentException] should be thrownBy send("")
    an [IllegalArgumentException] should be thrownBy sendln("")
    an [IllegalArgumentException] should be thrownBy returning("")
    an [IllegalArgumentException] should be thrownBy returningExpect(new Expect("ls", "", ConfigFactory.load()))
    an [IllegalArgumentException] should be thrownBy exit()
  }
  def illegalRegexWhenActions(e: Expect[String]): Unit = {
    import e._
    an [IllegalArgumentException] should be thrownBy send(m => "")
    an [IllegalArgumentException] should be thrownBy sendln(m => "")
    an [IllegalArgumentException] should be thrownBy returning(m => "")
    an [IllegalArgumentException] should be thrownBy returningExpect(m => new Expect(Seq("ls"), "", ConfigFactory.load()))
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
      e.expect("") {
        addActions(addWhenActions)
      }
      e.expect("".r) {
        addActions(addWhenActions)
        addActions(addRegexWhenActions)
      }
      e.expect(EndOfFile) {
        addActions(addWhenActions)
      }
      e.expect(Timeout) {
        addActions(addWhenActions)
      }
    }
    def addMultipleWhens(e: Expect[String]): Unit = {
      import e._
      when(""){
        addActions(addWhenActions)
      }
      when("".r){
        addActions(addWhenActions)
        addActions(addRegexWhenActions)
      }
      when(EndOfFile){
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
      returningExpect(new Expect("ls", "", ConfigFactory.load()))
      exit()
    }
    def addRegexWhenActions(e: Expect[String]): Unit = {
      import e._
      send(m => "")
      sendln(m => "")
      returning(m => "")
      returningExpect(m => new Expect(Seq("ls"), "", ConfigFactory.load()))
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
