package work.martins.simon.expect

package object dsl {
  //Useful conversion to use in returningExpect actions, which are waiting to receive a core.Expect
  implicit def dslExpectToCoreExpect[R](expect: Expect[R]): core.Expect[R] = expect.toCore
}
