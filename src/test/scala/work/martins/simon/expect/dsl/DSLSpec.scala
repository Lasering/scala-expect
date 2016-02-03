package work.martins.simon.expect.dsl

import org.scalatest.{Matchers, WordSpec}
import work.martins.simon.expect.fluent.StringWhen

class DSLSpec extends WordSpec with Matchers {
  "An Expect" when {
    "an expect block is added" should {
      "fail if the expect block is added inside another expect block" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = ()) {
            expect {
              expect {
                when(""){
                  send("")
                }
              }
            }
          }
        }
      }
      "work if the expect block is added directly to the body of the expect" in {
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
    "a when is added" should {
      "fail if the when is added directly to the body of the expect" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = ()) {
            when("") {
              send("")
            }
          }
        }
      }
      "fail if the when is added directly to the body of the expect with an expect block before" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = ()) {
            expect {
              when(""){
                send("")
              }
            }
            when("") {
              send("")
            }
          }
        }
      }
      "fail if the when is added directly to the body of the expect with an expect block after" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = ()) {
            when("") {
              send("")
            }
            expect {
              when("") {
                send("")
              }
            }
          }
        }
      }
      "fail if the when is added inside another when" in {
        intercept[IllegalArgumentException] {
          new Expect("ls", defaultValue = ()) {
            expect {
              when("") {
                when("") {
                  send("")
                }
              }
            }
          }
        }
      }
      "work if the when is added inside an expect" in {
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


    "asdasd" should {
      "cenas" in {
        type E = Either[Int, String]
        def principalDoesNotExist(expectBlock: ExpectBlock[E]): When[E, StringWhen[E]] = {
          import expectBlock._
          when("Principal does not exist") {
            returning(Left(5))
          }
        }
        val defaultValue: Either[Int, String] = Left(1)
        new Expect("ls", defaultValue) {
          expect {
            addWhen(principalDoesNotExist)
            when("cenas") {
              exit()
            }
          }
        }
      }
    }
  }
}
