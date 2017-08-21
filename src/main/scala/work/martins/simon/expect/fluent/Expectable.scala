package work.martins.simon.expect.fluent

import scala.util.matching.Regex

import work.martins.simon.expect.{EndOfFile, FromInputStream, Timeout}

trait Expectable[R] {
  protected val expectableParent: Expect[R] //The root of an Expectable must be an Expect

  /**
   * Adds an empty new `ExpectBlock`.
   * @return the new `ExpectBlock`.
   */
  def expect: ExpectBlock[R] = expectableParent.expect
  /**
    * Adds an empty new `ExpectBlock` reading from the specified `FromInputStream`.
    * @param readFrom from which `FromInputStream` to read the output.
    * @return the new `ExpectBlock`.
    */
  def expect(readFrom: FromInputStream): ExpectBlock[R] = expectableParent.expect(readFrom)
  
  // Shortcuts

  /**
   * Adds, in a new `ExpectBlock`, a `StringWhen` that matches whenever `pattern` is contained
   * in the text read from the StdOut output. This is a shortcut to `expect.when(pattern)`.
   * @param pattern the pattern to be used in the `StringWhen`.
   * @return the new `StringWhen`.
   */
  def expect(pattern: String): StringWhen[R] = expect.when(pattern)
  /**
    * Adds, in a new `ExpectBlock`, a `StringWhen` that matches whenever `pattern` is contained
    * in the text read from the specified `FromInputStream`. This is a shortcut to `expect.when(pattern, readFrom)`.
    * @param pattern the pattern to be used in the `StringWhen`.
    * @param readFrom from which `FromInputStream` to read the output.
    * @return the new `StringWhen`.
    */
  def expect(pattern: String, readFrom: FromInputStream): StringWhen[R] = expect.when(pattern, readFrom)
  
  /**
   * Adds, in a new `ExpectBlock`, a `RegexWhen` that matches whenever the regex `pattern` successfully matches
   * against the text read from the StdOut output. This is a shortcut to `expect.when(pattern)`.
   * @param pattern the pattern to be used in the `RegexWhen`.
   * @return the new `RegexWhen`.
   */
  def expect(pattern: Regex): RegexWhen[R] = expect.when(pattern)
  /**
    * Adds, in a new `ExpectBlock`, a `RegexWhen` that matches whenever the regex `pattern` successfully matches
    * against the text read from the specified `FromInputStream`. This is a shortcut to `expect.when(pattern, readFrom)`.
    * @param pattern the pattern to be used in the `RegexWhen`.
    * @param readFrom from which `FromInputStream` to read the output.
    * @return the new `RegexWhen`.
    */
  def expect(pattern: Regex, readFrom: FromInputStream): RegexWhen[R] = expect.when(pattern, readFrom)

  /**
   * Adds, in a new `ExpectBlock`, a `EndOfFileWhen` that matches whenever the end of file is reached while trying to
   * read from the StdOut output. This is a shortcut to `expect.when(pattern)`.
   * @param pattern the pattern to be used in the `EndOfFileWhen`.
   * @return the new `RegexWhen`.
   */
  def expect(pattern: EndOfFile.type): EndOfFileWhen[R] = expect.when(pattern)
  /**
    * Adds, in a new `ExpectBlock`, a `EndOfFileWhen` that matches whenever the end of file is reached while trying to
    * read from the specified `FromInputStream`. This is a shortcut to `expect.when(pattern, readFrom)`.
    * @param pattern the pattern to be used in the `EndOfFileWhen`.
    * @param readFrom from which `FromInputStream` to read the output.
    * @return the new `RegexWhen`.
    */
  def expect(pattern: EndOfFile.type, readFrom: FromInputStream): EndOfFileWhen[R] = expect.when(pattern, readFrom)
  
  /**
    * Adds, in a new `ExpectBlock`, a `TimeoutWhen` that matches whenever the read from any of the `FromStreamInput`s
    * times out. This is a shortcut to `expect.when(pattern)`.
    * @param pattern the pattern to be used in the `TimeoutWhen`.
    * @return the new `RegexWhen`.
    */
  def expect(pattern: Timeout.type): TimeoutWhen[R] = expect.when(pattern)
  
  /**
    * Add arbitrary `ExpectBlock`s to this `Expect`.
    *
    * This is helpful to refactor code. For example: imagine you have an error case you want to add to multiple expects.
    * You could leverage this method to do so in the following way:
    * {{{
    *   def errorCaseExpectBlock(expect: Expect[String]): Unit {
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
