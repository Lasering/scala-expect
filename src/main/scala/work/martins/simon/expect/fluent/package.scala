package work.martins.simon.expect

package object fluent {
  implicit def fluentExpectToCoreExpect[R](expect: Expect[R]): core.Expect[R] = {
    new core.Expect[R](expect.command, expect.defaultValue, expect.settings.config)(expect.expects.map(_.toCore):_*)
  }
}
