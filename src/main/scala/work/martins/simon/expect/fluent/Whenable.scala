package work.martins.simon.expect.fluent

import work.martins.simon.expect.{EndOfFile, FromInputStream, Timeout}
import work.martins.simon.expect.FromInputStream.StdOut
import scala.util.matching.Regex

trait Whenable[R] extends Expectable[R]
  protected val whenableParent: ExpectBlock[R] //The root of an Whenable must be an ExpectBlock
  given ExpectBlock[R] = whenableParent
  protected val expectableParent: Expect[R] = whenableParent.parent

  /**
   * Adds a new `StringWhen` that matches whenever `pattern` is contained
   * in the text read from the `FromInputStream` specified in the parent ExpectBlock.
   * @param pattern the pattern to match against.
   * @return the new `StringWhen`.
   */
  def when(pattern: String): StringWhen[R] = when(pattern, StdOut)
  /**
    * Adds a new `StringWhen` that matches whenever `pattern` is contained
    * in the text read from the specified `FromInputStream`.
    * @param pattern the pattern to match against.
    * @param readFrom from which `FromInputStream` to read the output.
    * @return the new `StringWhen`.
    */
  def when(pattern: String, readFrom: FromInputStream): StringWhen[R] = whenableParent.when(pattern, readFrom)

  /**
   * Adds a new `RegexWhen` that matches whenever the regex `pattern` successfully matches
   * against the text read from `FromInputStream` specified in the parent ExpectBlock.
   * @param pattern the pattern to match against.
   * @return the new `RegexWhen`.
   */
  def when(pattern: Regex): RegexWhen[R] = when(pattern, StdOut)
  /**
    * Adds a new `RegexWhen` that matches whenever the regex `pattern` successfully matches
    * against the text read from the specified `FromInputStream`.
    * @param pattern the pattern to match against.
    * @param readFrom from which `FromInputStream` to read the output.
    * @return the new `RegexWhen`.
    */
  def when(pattern: Regex, readFrom: FromInputStream): RegexWhen[R] = whenableParent.when(pattern, readFrom)

  /**
    * Adds a new `EndOfFileWhen` that matches whenever the EndOfFile in read from `FromInputStream`
    * specified in the parent ExpectBlock.
    * @param pattern the pattern to match against.
    * @return the new `EndOfFileWhen`.
    */
  def when(pattern: EndOfFile.type): EndOfFileWhen[R] = when(pattern, StdOut)
  /**
    * Adds a new `EndOfFileWhen` that matches whenever the EndOfFile in read from the specified `FromInputStream`.
    * @param pattern the pattern to match against.
    * @param readFrom from which `FromInputStream` to read the output.
    * @return the new `EndOfFileWhen`.
    */
  def when(pattern: EndOfFile.type, readFrom: FromInputStream): EndOfFileWhen[R] = whenableParent.when(pattern, readFrom)

  /**
   * Adds a new `TimeoutWhen` that matches whenever the read from any of the `FromStreamInput`s times out.
   * @param pattern the pattern to match against.
   * @return the new `TimeoutWhen`.
   */
  def when(pattern: Timeout.type): TimeoutWhen[R] = whenableParent.when(pattern)

  /**
    * Add an arbitrary `When` to this `ExpectBlock`.
    *
    * This is helpful to refactor code. For example: imagine you have an error case you want to add to
    * multiple `ExpectBlock`s. You could leverage this method to do so in the following way:
    * {{{
    *   def errorCaseWhen(expectBlock: ExpectBlock[String]): When[String] = {
    *     expectBlock
    *       .when("Some error")
    *         .returning("Got some error")
    *   }
    *
    *   def parseOutputA: Expect[String] = {
    *     val e = new Expect("some command", "")
    *     e.expect
    *       .when(...)
    *         .sendln(...)
    *     e.expect
    *       .addWhen(errorCaseWhen)
    *         .exit()
    *   }
    *
    *   def parseOutputB: Expect[String] = {
    *     val e = new Expect("some command", "")
    *     e.expect
    *       .when(...)
    *         .sendln(..)
    *         .returning(...)
    *       .addWhen(errorCaseWhen)
    *   }
    * }}}
    *
    * This function returns the added When which allows you to add further actions, see the exit action of the
    * `parseOutputA` method in the above code.
    *
    * It is possible to add more than one When using this method, however this is discouraged since it will make the
    * code somewhat more illegible because "hidden" Whens will also be added.
    *
    * If you need to add more than one When you have two options:
    *
    *  1. {{{
    *    e.expect
    *      .when(...)
    *         .sendln(..)
    *         .returning(...)
    *      .addWhen(errorCaseWhen)
    *      .addWhen(anotherWhen)
    *  }}}
    *  1. {{{
    *    e.expect
    *      .when(...)
    *         .sendln(..)
    *         .returning(...)
    *      .addWhens(multipleWhens)
    *  }}}
    *
    * @param f function that adds the `When`.
    * @return the added `When`.
    */
  def addWhen[W <: When[R]](f: ExpectBlock[R] => W): W = whenableParent.addWhen(f)
  /**
    * Add arbitrary `When`s to this `ExpectBlock`.
    *
    * This method is very similar to the `addWhen` with the following differences:
    *  1. `f` has a more relaxed type.
    *  1. It returns this ExpectBlock. Which effectively prohibits you from invoking When methods.
    *  1. Has a more semantic name when it comes to adding multiple When's.
    *
    * @param f function that adds `When`s.
    * @return this ExpectBlock.
    */
  def addWhens(f: ExpectBlock[R] => When[R]): ExpectBlock[R] = whenableParent.addWhens(f)
