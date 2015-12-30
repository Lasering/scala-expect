package work.martins.simon.expect

package object fluent {
  //Useful conversion to use in returningExpect actions, which are waiting to receive a core.Expect
  implicit def fluentExpectToCoreExpect[R](expect: Expect[R]): core.Expect[R] = {
    new core.Expect[R](expect.command, expect.defaultValue, expect.settings)(expect.expects.map(_.toCore):_*)
  }
}
