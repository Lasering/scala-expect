package work.martins.simon.expect.fluent

import work.martins.simon.expect.{Timeout, EndOfFile}

import scala.util.matching.Regex

trait Expectable[R] {
  protected val expectableParent: Expectable[R]

  /**
   * Adds an empty new `ExpectBlock`.
   * @return the new `ExpectBlock`.
   */
  def expect: ExpectBlock[R] = expectableParent.expect
  /**
   * Adds, in a new `ExpectBlock`, a `StringWhen` that matches whenever `pattern` is contained
   * in the text read from the process output (stdOut). This is a shortcut to `expect.when(pattern)`.
   * @param pattern the pattern to be used in the `StringWhen`.
   * @return the new `StringWhen`.
   */
  def expect(pattern: String): StringWhen[R] = expect.when(pattern)
  /**
   * Adds, in a new `ExpectBlock`, a `RegexWhen` that matches whenever the regex `pattern` successfully matches
   * against the text read from the process output (stdOut). This is a shortcut to `expect.when(pattern)`.
   * @param pattern the pattern to be used in the `RegexWhen`.
   * @return the new `RegexWhen`.
   */
  def expect(pattern: Regex): RegexWhen[R] = expect.when(pattern)
  /**
   * Adds, in a new `ExpectBlock`, a `TimeoutWhen` that matches whenever the read from the process output (stdOut)
   * times out. This is a shortcut to `expect.when(pattern)`.
   * @param pattern the pattern to be used in the `TimeoutWhen`.
   * @return the new `RegexWhen`.
   */
  def expect(pattern: Timeout.type): TimeoutWhen[R] = expect.when(pattern)
  /**
   * Adds, in a new `ExpectBlock`, a `EndOfFileWhen` that matches whenever the end of file is reached while trying to
   * read from the process output (stdOut). This is a shortcut to `expect.when(pattern)`.
   * @param pattern the pattern to be used in the `EndOfFileWhen`.
   * @return the new `RegexWhen`.
   */
  def expect(pattern: EndOfFile.type): EndOfFileWhen[R] = expect.when(pattern)

  /**
    * Add arbitrary `ExpectBlock`s to this `Expect`.
    *
    * This is helpful to refactor code. For example: imagine you have an error case you want to add to multiple expects.
    * You could leverage this method to do so in the following way:
    * {{{
    *   def errorCaseExpectBlock: Expect[String] => Unit = { expect =>
    *     expect.expect
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
  def addExpectBlock(f: Expect[R] => Unit): Expect[R] = expectableParent.addExpectBlock(f)
}
