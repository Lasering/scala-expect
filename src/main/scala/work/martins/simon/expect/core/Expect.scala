package work.martins.simon.expect.core

import scala.concurrent._
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.Settings
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core.RunContext.{ChangeToNewExpect, Continue, Terminate}

/**
  * Expect allows you to invoke CLIs and ensure they return what you expect.
  * You can describe a CLI interaction which will be executed when `run` is invoked.
  *
  * Each interaction is described inside an `ExpectBlock` using `When`s. Each `When` states under which
  * circumstances the corresponding actions are to be executed. For example:
  *
  * {{{
  * import work.martins.simon.core._
  * import scala.concurrent.ExecutionContext.Implicits.global
  *
  * val e = new Expect("bc -i", defaultValue = 5)(
  *   ExpectBlock(
  *     When("For details type `warranty'.")(
  *       Sendln("1 + 2")
  *     )
  *   ),
  *   ExpectBlock(
  *     When("""\n(\d+)\n""".r)(
  *       Sendln { m =>
  *         val previousAnswer = m.group(1)
  *         println(s"Got $$previousAnswer")
  *         s"$$previousAnswer + 3"
  *       }
  *     )
  *   ),
  *   ExpectBlock(
  *     When("""\n(\d+)\n""".r)(
  *       Returning(_.group(1).toInt)
  *     )
  *   )
  * )
  * e.run() //Returns 6 inside a Future[Int]
  * }}}
  *
  * We are executing `bc -i` which is a CLI calculator. On startup `bc` writes to the StdOut:
  *   "For details type `warranty'."
  * The expect we created reacts to it by sending to the StdIn the string `1 + 2`.
  *
  * It then proceeds to the next `ExpectBlock`, which matches text from StdOut using the regex `\n(\d+)\n`, and sending the
  * string `3 + 3` where the first `3` is obtained by inspecting the regex match and extracting the first group.
  *
  * Finally the last interaction is very similar to the second one, with the only difference being the matched digit is
  * not used to send more text to `bc` but rather to set the value the expect will return in the end of the execution.
  *
  * If you find the syntax used to create the $type too verbose you should look at [[work.martins.simon.expect.fluent.Expect]]
  * or [[work.martins.simon.expect.dsl.Expect]] which provide a more terse syntax, especially `dsl.Expect` which provides
  * a syntax very similar to Tcl Expect.
  *
  * @param command the command this $type will execute.
  * @param defaultValue the value that will be returned if no `Returning` action is executed.
  * @param settings the settings that parameterize the execution of this $type.
  * @param expectBlocks the `ExpectBlock`s that describe the interactions this $type will execute.
  * @tparam R the type this $type returns.
  *
  * @define type `Expect`
  */
final case class Expect[+R](command: Seq[String], defaultValue: R, settings: Settings = Settings.fromConfig())
                           (val expectBlocks: ExpectBlock[R]*) extends LazyLogging {
  def this(command: String, defaultValue: R, settings: Settings)(expectBlocks: ExpectBlock[R]*) =
    this(splitBySpaces(command), defaultValue, settings)(expectBlocks:_*)

  def this(command: String, defaultValue: R)(expectBlocks: ExpectBlock[R]*) =
    this(command, defaultValue, Settings.fromConfig())(expectBlocks:_*)

  require(command.nonEmpty, "Expect must have a command to run.")

  /**
    * Runs this Expect using the given `settings`. Or in other words overrides the Settings set at construction time.
    * 
    * In order to uniformly scale the timeouts just change `settings.timeFactor` to a value bigger than 1.
    * 
    * @param propagateSettings whether to use the given `settings` when executing the Expects returned by this Expect.
    *                          For example if set to true and this Expect contains `ReturningExpect` then the returned
    *                          Expect will be executed using `settings` instead of the Settings with which it has created.                          
    * @param ex
    * @return
    */
  def run(settings: Settings = this.settings, propagateSettings: Boolean = false)(implicit ex: ExecutionContext): Future[R] =
    run(NuProcessRichProcess(command, settings), propagateSettings)

  private[this] def runExpectBlocks(expectBlocks: Seq[ExpectBlock[R]], runContext: RunContext[R], propagateSettings: Boolean)
                                   (implicit ex: ExecutionContext): Future[R] = {
    if (expectBlocks.isEmpty) Future.successful(runContext.value)
    else expectBlocks.head.run(runContext).flatMap { innerRunContext =>
      innerRunContext.executionAction match {
        case Continue => runExpectBlocks(expectBlocks.tail, innerRunContext, propagateSettings)
        case Terminate => Future.successful(innerRunContext.value)
        case ChangeToNewExpect(newExpect) =>
          innerRunContext.process.destroy()
          val expect = newExpect.asInstanceOf[Expect[R]]
          val runSettings = if (propagateSettings) innerRunContext.settings else expect.settings
          expect.run(runSettings)
      }
    }
  }

  def run(process: RichProcess, propagateSettings: Boolean)(implicit ex: ExecutionContext): Future[R] = {
    val runContext = RunContext(process, value = defaultValue, executionAction = Continue)

    logger.info(runContext.withId("Running command: " + command.mkString("\"", " ", "\"")))
    logger.debug(runContext.withId(runContext.settings.toString))

    runExpectBlocks(expectBlocks, runContext, propagateSettings).transformWith { result =>
      logger.info(runContext.withId(s"Finished with $result"))
      Future(runContext.process.destroy()).flatMap(_ => Future.fromTry(result))
    }
  }

  /** Creates a new $type by applying a function to this $type result.
    * 
    * @tparam T the type of the returned $type
    * @param f the function which will be applied to this $type result
    * @return an $type which will return the result of the application of the function `f`
    * @group Transformations
    */
  def map[T](f: R => T): Expect[T] = Expect(command, f(defaultValue), settings)(expectBlocks.map(_.map(f)):_*)

  /** Creates a new $type by applying a function to this $type result, and returns the result of the function as the new $type.
    *
    * @tparam T the type of the returned $type
    * @param f the function which will be applied to this $type result
    * @return the $type returned as the result of the application of the function `f`
    * @group Transformations
    */
  def flatMap[T](f: R => Expect[T]): Expect[T] = Expect(command, f(defaultValue).defaultValue, settings)(expectBlocks.map(_.flatMap(f)):_*)

  /** Creates a new $type with one level of nesting flattened, this method is equivalent to `flatMap(identity)`.
    * @tparam T the type of the returned $type
    * @return an $type with one level of nesting flattened
    * @group Transformations
    */
  def flatten[T](implicit ev: R <:< Expect[T]): Expect[T] = flatMap(ev)

  private def notDefined[RR >: R](function: String, text: String)(result: RR) = {
    throw new NoSuchElementException(s"""Expect.$function: $text "$result"""")
  }

  /** Creates a new $type by filtering its result with a predicate.
    *
    * If the current $type result satisfies the predicate, the new $type will also hold that result.
    * Otherwise, the resulting $type will fail with a `NoSuchElementException`.
    *
    * @param p the predicate to apply to the result of this $type
    * @group Transformations
    */
  def filter(p: R => Boolean): Expect[R] = map { r =>
    if (p(r)) r else notDefined("filter", "predicate is not satisfied for")(r)
  }

  /** Used by for-comprehensions.
    * @group Transformations
    */
  def withFilter(p: R => Boolean): Expect[R] = filter(p) // Expect is already lazy, so we can simply call filter.

  /** Creates a new $type by mapping the result of the current $type, if the given partial function is defined at that value.
    *
    * If the current $type contains a value for which the partial function is defined, the new $type will also hold that value.
    * Otherwise, the resulting $type will fail with a `NoSuchElementException`.
    *
    * @tparam T the type of the returned $type
    * @param pf the `PartialFunction` to apply to the result of this $type
    * @return an $type holding the result of application of the `PartialFunction` or a `NoSuchElementException`
    * @group Transformations
    */
  def collect[T](pf: PartialFunction[R, T]): Expect[T] = map { r =>
    pf.applyOrElse(r, notDefined("collect", "partial function is not defined at"))
  }

  /** Creates a new $type by flatMapping the result of the current $type, if the given partial function is defined at that value.
    *
    * If the current $type contains a value for which the partial function is defined, the new $type will also hold that value.
    * Otherwise, the resulting $type will fail with a `NoSuchElementException`.
    *
    * @tparam T the type of the returned $type
    * @param pf the `PartialFunction` to apply to the result of this $type
    * @return an $type holding the result of application of the `PartialFunction` or a `NoSuchElementException`
    * @group Transformations
    */
  def flatCollect[T](pf: PartialFunction[R, Expect[T]]): Expect[T] = flatMap { r =>
    pf.applyOrElse(r, notDefined("flatCollect", "partial function is not defined at"))
  }

  // TODO improve the example
  /** Transform this $type result using the following strategy:
    *  - if `flatMapPF` is defined for this expect result then flatMap the result using flatMapPF.
    *  - otherwise, if `mapPF` is defined for this expect result then map the result using mapPF.
    *  - otherwise a NoSuchElementException is thrown where the result would be expected.
    *
    * This function is very useful when you need to flatMap this $type for some values of its result type and map
    * this $type for some other values of its result type. In other words it `collect`s and `flatCollect`s this $type simultaneously.
    *
    * To ensure you don't get NoSuchElementException you should take special care in ensuring:
    * {{{ domain(flatMapPF) ∪ domain(mapPF) == domain(R) }}}
    * Remember that in the domain of R the `defaultValue` is also included.
    *
    * @example {{{
    * def countFilesInFolder(folder: String): Expect[Either[String, Int]] = {
    *   val e = new Expect(s"ls -1 $$folder", Left("unknownError"): Either[String, Int])(
    *     ExpectBlock(
    *       StringWhen("access denied")(
    *         Returning(Left("Access denied"))
    *       ),
    *       RegexWhen("(?s)(.*)".r)(
    *         ReturningWithRegex(_.group(1).split("\n").length)
    *       )
    *     )
    *   )
    *   e
    * }
    *
    * def ensureFolderIsEmpty(folder: String): Expect[Either[String, Unit]] = {
    *   countFilesInFolder(folder).transform({
    *     case Right(numberOfFiles) if numberOfFiles > 0 =>
    *       Expect(s"rm -r $$folder", Left("unknownError"): Either[String, Unit])(
    *         ExpectBlock(
    *           StringWhen("access denied")(
    *             Returning(Left("Access denied"))
    *           ),
    *           EndOfFileWhen(
    *             Returning(())
    *           )
    *         )
    *       )
    *   }, {
    *     case Left(l) => Left(l) // Propagate the error
    *     case Right(0) => Right(())
    *   })
    * }
    * }}}
    * @tparam T the type of the returned $type
    * @param flatMapPF the function that will be applied when a flatMap is needed
    * @param mapPF the function that will be applied when a map is needed
    * @return a new $type whose result is either flatMapped or mapped according to whether flatMapPF or
    *         mapPF is defined for the given result
    * @group Transformations
    */
  def transform[T](flatMapPF: PartialFunction[R, Expect[T]], mapPF: PartialFunction[R, T]): Expect[T] = {
    val newDefaultValue = flatMapPF.andThen(_.defaultValue).orElse(mapPF)
      .applyOrElse(defaultValue, notDefined("transform", "neither flatMapPF nor mapPF are defined at the Expect default value"))

    new Expect[T](command, newDefaultValue, settings)(expectBlocks.map(_.transform(flatMapPF, mapPF)):_*)
  }

  /** Zips the results of `this` and `that` $type, and creates a new $type holding the tuple of their results.
    *
    * @tparam T the type of the returned $type
    * @param that the other $type
    * @return an $type with the results of both ${type}s
    * @group Transformations
    */
  def zip[T](that: Expect[T]): Expect[(R, T)] = zipWith(that)((r1, r2) => (r1, r2))

  /** Zips the results of `this` and `that` $type using a function `f`,
    *  and creates a new $type holding the result.
    *
    * @tparam T the type of the other $type
    * @tparam U the type of the resulting $type
    * @param that the other $type
    * @param f the function to apply to the results of `this` and `that`
    * @return an $type with the result of the application of `f` to the results of `this` and `that`
    * @group Transformations
    */
  def zipWith[T, U](that: Expect[T])(f: (R, T) => U): Expect[U] = flatMap(r1 => that.map(r2 => f(r1, r2)))

  override def toString: String =
    s"""Expect:
       |\tCommand: $command
       |\tDefaultValue: $defaultValue
       |\tSettings: $settings
       |${expectBlocks.mkString("\n").indent()}""".stripMargin

  /**
    * Returns whether `other` is an $type with the same `command`, the same `defaultValue`, the same `settings` and
    * the same `expectBlocks` as this $type.
    *
    * If any `expectBlock` contains an Action with a function, eg. Returning, this method will return false,
    * because equality is not defined for functions.
    *
    * The method `structurallyEqual` can be used to test whether two expects have the same structure.
    *
    * @param other the other $type to compare this $type to.
    */
  override def equals(other: Any): Boolean = other match {
    case that: Expect[R] =>
      command == that.command &&
      defaultValue == that.defaultValue &&
      settings == that.settings &&
      expectBlocks == that.expectBlocks
    case _ => false
  }

  /**
    * @define subtypes expect blocks
    * Returns whether the other $type has the same
    *  - command
    *  - defaultvalue
    *  - settings
    *  - number of $subtypes and that each pair of $subtypes is structurally equal as this $type.
    *
    * @param other the other $type to compare this $type to.
    */
  def structurallyEquals[RR >: R](other: Expect[RR]): Boolean =
    command == other.command &&
    defaultValue == other.defaultValue &&
    settings == other.settings &&
    expectBlocks.size == other.expectBlocks.size &&
    expectBlocks.zip(other.expectBlocks).forall{ case (a, b) => a.structurallyEquals(b) }

  override def hashCode(): Int =
    Seq(command, defaultValue, settings, expectBlocks)
      .map(_.hashCode())
      .foldLeft(0)((a, b) => 31 * a + b)
}