package work.martins.simon.expect.fluent

trait Expectable[R] {
  protected val expectableParent: Expect[R] //The root of an Expectable must be an Expect

  /**
   * Adds a new `ExpectBlock`.
   * @return the new `ExpectBlock`.
   */
  def expect: ExpectBlock[R] = expectableParent.expect

  /**
    * Add arbitrary `ExpectBlock`s to this `Expect`.
    *
    * This is helpful to refactor code. For example: imagine you have an error case you want to add to multiple expects.
    * You could leverage this method to do so in the following way:
    * {{{
    *   def errorCaseExpectBlock(e: Expect[String]): Unit {
    *     e.expect
    *       .when("Some error")
    *         .returning("Got some error")
    *   }
    *
    *   //Then in your expects
    *   def parseOutputA: Expect[String] = {
    *     val e = new Expect("some command", "")
    *     e.expect(...)
    *     e.expect
    *       .when(...)
    *         .action1
    *       .when(...)
    *         .action2
    *     e.addExpectBlock(errorCaseExpectBlock)
    *   }
    *
    *   def parseOutputB: Expect[String] = {
    *     val e = new Expect("some command", "")
    *     e.expect
    *       .when(...)
    *         .action1
    *         .action2
    *       .when(...)
    *         .action1
    *     e.expect(...)
    *       .action2
    *     e.addExpectBlock(errorCaseExpectBlock)
    *   }
    * }}}
    *
    * @param f function that adds `ExpectBlock`s.
    * @return this `Expect`.
    */
  def addExpectBlock(f: Expect[R] => ExpectBlock[R]): Expect[R] = expectableParent.addExpectBlock(f)
  
  // TODO create scalafix rules to migrate the expect shortcuts to the new code
}
