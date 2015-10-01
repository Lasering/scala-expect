package codes.simon.expect.fluent

import codes.simon.expect.core.{Timeout, EndOfFile}

import scala.util.matching.Regex

trait Whenable[R] {
  val whenableParent: Whenable[R]

  /**
   * Adds a new `StringWhen` that matches whenever `pattern` is contained
   * in the text read from the process output (stdOut).
   * @param pattern the pattern to match against.
   * @return the new `StringWhen`.
   */
  def when(pattern: String): StringWhen[R] = whenableParent.when(pattern)
  /**
   * Adds a new `RegexWhen` that matches whenever the regex `pattern` successfully matches
   * against the text read from the process output (stdOut).
   * @param pattern the pattern to match against.
   * @return the new `RegexWhen`.
   */
  def when(pattern: Regex): RegexWhen[R] = whenableParent.when(pattern)
  /**
   * Adds a new `EndOfFileWhen` that matches whenever the EndOfFile in the process output (stdOut) is reached.
   * @param pattern the pattern to match against.
   * @return the new `EndOfFileWhen`.
   */
  def when(pattern: EndOfFile.type): EndOfFileWhen[R] = whenableParent.when(pattern)
  /**
   * Adds a new `TimeoutWhen` that matches whenever a Timeout in thrown while trying to read text
   * from the process output (stdOut).
   * @param pattern the pattern to match against.
   * @return the new `TimeoutWhen`.
   */
  def when(pattern: Timeout.type): TimeoutWhen[R] = whenableParent.when(pattern)
}
