package work.martins.simon.expect

package object fluent {
  //Useful conversion to use in returningExpect actions, which are waiting to receive a core.Expect
  implicit def fluentToCoreExpect[R](expect: Expect[R]): core.Expect[R] = expect.toCore
}
