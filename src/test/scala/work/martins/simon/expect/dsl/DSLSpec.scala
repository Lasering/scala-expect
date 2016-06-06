package work.martins.simon.expect.dsl

import org.scalatest.{Matchers, WordSpec}
import work.martins.simon.expect.{EndOfFile, Timeout}

class DSLSpec extends WordSpec with Matchers {
  "An Expect" when {
    "an expect block is added" should {
      "fail if the expect block is added inside another expect block" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = ()) {
            expect {
              expect("") {
                send("")
              }
            }
          }
        }
      }
      "work if the expect block is added directly to the body of the expect" in {
        noException should be thrownBy {
          new Expect("ls", defaultValue = "") {
            expect {
              when("".r) {
                send(m => "")
              }
            }
          }
        }
      }
    }
    "a when is added" should {
      "fail if the when is added directly to the body of the expect" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = "") {
            when("") {
              sendln("")
            }
          }
        }
      }
      "fail if the when is added directly to the body of the expect with an expect block before" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = "") {
            expect(EndOfFile){
              returning("")
            }
            when("".r) {
              sendln(m => "")
            }
          }
        }
      }
      "fail if the when is added directly to the body of the expect with an expect block after" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = "") {
            when(EndOfFile) {
              returning("")
            }
            expect(Timeout) {
              exit()
            }
          }
        }
      }
      "fail if the when is added inside another when" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = "") {
            expect {
              when(Timeout) {
                when("") {
                  returningExpect(new Expect("ls", ""))
                }
              }
            }
          }
        }
      }
      "work if the when is added inside an expect" in {
        noException should be thrownBy {
          new Expect("ls", defaultValue = "") {
            expect {
              when("".r) {
                returning(m => "")
                returningExpect(m => new Expect("ls", ""))
              }
            }
          }
        }
      }
    }
    "an action is added" should {
      "fail if the action is added directly to the body of the expect" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = ()) {
            send("")
          }
        }
      }
      "fail if the action is added directly to an expect block" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = ()) {
            expect {
              send("")
            }
          }
        }
      }
      "work if the action is added inside a when" in {
        noException should be thrownBy {
          new Expect("ls", defaultValue = ()) {
            expect {
              when("") {
                send("")
              }
            }
          }
        }
      }
    }
  }
}
