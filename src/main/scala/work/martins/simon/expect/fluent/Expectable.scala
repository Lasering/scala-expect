package work.martins.simon.expect.fluent

import scala.util.matching.Regex

import work.martins.simon.expect.core.{EndOfFile, Timeout}

trait Expectable[R] {
  val expectableParent: Expectable[R]

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
}
