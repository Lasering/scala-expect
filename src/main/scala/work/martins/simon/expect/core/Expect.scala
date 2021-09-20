package work.martins.simon.expect.core

import scala.concurrent.*
import scala.util.{Failure, Success}
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.{/=>, Settings}
import work.martins.simon.expect.StringUtils.*
import work.martins.simon.expect.core.ExecutionAction.*

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
  * If you find the syntax used to create the Expect too verbose you should look at [[work.martins.simon.expect.fluent.Expect]]
  * or [[work.martins.simon.expect.dsl.Expect]] which provide a more terse syntax, especially `dsl.Expect` which provides
  * a syntax very similar to Tcl Expect.
  *
  * @param command the command this Expect will execute.
  * @param defaultValue the value that will be returned if no `Returning` action is executed.
  * @param settings the settings that parameterize the execution of this Expect.
  * @param expectBlocks the `ExpectBlock`s that describe the interactions this Expect will execute.
  * @tparam R the type this Expect returns.
  */
final case class Expect[+R](command: Seq[String] | String, defaultValue: R, settings: Settings = Settings.fromConfig())
                           (val expectBlocks: ExpectBlock[R]*) extends LazyLogging derives CanEqual:
  val commandSeq: Seq[String] = properCommand(command)
  require(commandSeq.nonEmpty, "Expect must have a command to run.")
  
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
  def run(settings: Settings = this.settings, propagateSettings: Boolean = false)(using ExecutionContext): Future[R] =
    run(NuProcessRichProcess(commandSeq, settings), propagateSettings)
  
  private def success(runContext: RunContext[R])(using ex: ExecutionContext): Future[R] = Future {
    runContext.process.destroy()
  }.map { _ =>
    logger.info(runContext.withId(s"Finished returning: ${runContext.value}"))
    runContext.value
  }
  
  /**
    * Runs this Expect using the specified `process`.
    * This is the default interpreter using Future.
    * @param process a Process capable of reading from StdOut and StdErr, as well as writing to StdOut.
    * @param propagateSettings whether nested Expects should use the same settings as this Expect.
    * @return a Future with the returned result. If no `Returning` action is specified the `defaultValue` will be used.
    */
  def run(process: RichProcess, propagateSettings: Boolean)(using ExecutionContext): Future[R] =
    val runContext = RunContext(process, value = defaultValue, executionAction = Continue)
    
    logger.info(runContext.withId("Running command: " + commandSeq.mkString("\"", " ", "\"")))
    logger.debug(runContext.withId(runContext.settings.toString))
    
    def innerRun(expectBlocks: Seq[ExpectBlock[R]], runContext: RunContext[R]): Future[R] =
      // TODO: how to implement this in a tail recursive way?
      expectBlocks.headOption.map { headExpectBlock =>
        //We still have expect blocks to run
        val result = headExpectBlock.run(runContext).flatMap { innerRunContext =>
          innerRunContext.executionAction match
            case Continue => innerRun(expectBlocks.tail, innerRunContext)
            case Terminate => success(innerRunContext)
            case ChangeToNewExpect(newExpect) =>
              innerRunContext.process.destroy()
              // How does this cast work?
              newExpect.run(if propagateSettings then runContext.settings else newExpect.settings).asInstanceOf[Future[R]]
        }
        //If we get an exception while running the head expect block we want to make sure the rich process is destroyed.
        result.transformWith {
          case Success(r) => Future.successful(r) // The success function already destroys the process
          case Failure(t) => Future { runContext.process.destroy() }.map(_ => throw t)
        }
      }.getOrElse(success(runContext))
    
    innerRun(expectBlocks, runContext)
  
  /**
    * Creates a new Expect by applying a function to this Expect result.
    *
    * @tparam T the type of the returned Expect
    * @param f the function which will be applied to this Expect result
    * @return an Expect which will return the result of the application of the function `f`
    * @group Transformations
    */
  def map[T](f: R => T): Expect[T] =
    Expect(command, f(defaultValue), settings)(expectBlocks.map(_.map(f))*)
  
  /**
    * Creates a new Expect by applying a function to this Expect result, and returns the result of the function as the new Expect.
    *
    * @tparam T the type of the returned Expect
    * @param f the function which will be applied to this Expect result
    * @return the Expect returned as the result of the application of the function `f`
    * @group Transformations
    */
  def flatMap[T](f: R => Expect[T]): Expect[T] =
    Expect(command, f(defaultValue).defaultValue, settings)(expectBlocks.map(_.flatMap(f))*)
  
  /**
    * Creates a new Expect with one level of nesting flattened, this method is equivalent to `flatMap(identity)`.
    * @tparam T the type of the returned Expect
    * @return an Expect with one level of nesting flattened
    * @group Transformations
    */
  def flatten[T](using ev: R <:< Expect[T]): Expect[T] = flatMap(ev)
  
  private def notDefined(function: String, text: String)(result: R): Nothing =
    throw new NoSuchElementException(s"""Expect.$function: $text "$result"""")
  
  /**
    * Creates a new Expect by filtering its result with a predicate.
    *
    * If the current Expect result satisfies the predicate, the new Expect will also hold that result.
    * Otherwise, the resulting Expect will fail with a `NoSuchElementException`.
    *
    * @param p the predicate to apply to the result of this Expect
    * @group Transformations
    */
  def filter(p: R => Boolean): Expect[R] = map { r =>
    if p(r) then r else notDefined("filter", "predicate is not satisfied for")(r)
  }
  
  /**
    * Used by for-comprehensions.
    * Expect is already lazy this is just an alias for filter.
    * @group Transformations
    */
  def withFilter(p: R => Boolean): Expect[R] = filter(p) //
  
  /**
    * Creates a new Expect by mapping the result of the current Expect, if the given partial function is defined at that value.
    *
    * If the current Expect contains a value for which the partial function is defined, the new Expect will also hold that value.
    * Otherwise, the resulting Expect will fail with a `NoSuchElementException`.
    *
    * @tparam T the type of the returned Expect
    * @param pf the `PartialFunction` to apply to the result of this Expect
    * @return an Expect holding the result of application of the `PartialFunction` or a `NoSuchElementException`
    * @group Transformations
    */
  def collect[T](pf: PartialFunction[R, T]): Expect[T] = map { r =>
    pf.applyOrElse(r, notDefined("collect", "partial function is not defined at"))
  }
  
  /**
    * Creates a new Expect by flatMapping the result of the current Expect, if the given partial function is defined at that value.
    *
    * If the current Expect contains a value for which the partial function is defined, the new Expect will also hold that value.
    * Otherwise, the resulting Expect will fail with a `NoSuchElementException`.
    *
    * @tparam T the type of the returned Expect
    * @param pf the `PartialFunction` to apply to the result of this Expect
    * @return an Expect holding the result of application of the `PartialFunction` or a `NoSuchElementException`
    * @group Transformations
    */
  def flatCollect[T](pf: PartialFunction[R, Expect[T]]): Expect[T] = flatMap { r =>
    pf.applyOrElse(r, notDefined("flatCollect", "partial function is not defined at"))
  }
  
  // TODO improve the example
  /**
    * Transform this Expect result using the following strategy:
    *  - if `flatMapPF` is defined for this expect result then flatMap the result using flatMapPF.
    *  - otherwise, if `mapPF` is defined for this expect result then map the result using mapPF.
    *  - otherwise a NoSuchElementException is thrown where the result would be expected.
    *
    * This function is very useful when you need to flatMap this Expect for some values of its result type and map
    * this Expect for some other values of its result type. In other words it `collect`s and `flatCollect`s this Expect simultaneously.
    *
    * To ensure you don't get NoSuchElementException you should take special care in ensuring:
    * {{{ domain(flatMapPF) ∪ domain(mapPF) == domain(R) }}}
    * Remember that in the domain of R the `defaultValue` is also included.
    *
    * @example {{{
    * def countFilesInFolder(folder: String): Expect[Either[String, Int]] =
    *   new Expect(s"ls -1 $$folder", Left("unknownError"): Either[String, Int])(
    *     ExpectBlock(
    *       When("access denied")(
    *         Returning(Left("Access denied"))
    *       ),
    *       When("(?s)(.*)".r)(
    *         Returning(_.group(1).split("\n").length)
    *       )
    *     )
    *   )
    *
    * def ensureFolderIsEmpty(folder: String): Expect[Either[String, Unit]] =
    *   countFilesInFolder(folder).transform({
    *     case Right(numberOfFiles) if numberOfFiles > 0 =>
    *       Expect(s"rm -r $$folder", Left("unknownError"): Either[String, Unit])(
    *         ExpectBlock(
    *           String("access denied")(
    *             Returning(Left("Access denied"))
    *           ),
    *           When(EndOfFile)(
    *             Returning(())
    *           )
    *         )
    *       )
    *   }, {
    *     case Left(l) => Left(l) // Propagate the error
    *     case Right(0) => Right(())
    *   })
    * }}}
    * @tparam T the type of the returned Expect
    * @param flatMapPF the function that will be applied when a flatMap is needed
    * @param mapPF the function that will be applied when a map is needed
    * @return a new Expect whose result is either flatMapped or mapped according to whether flatMapPF or
    *         mapPF is defined for the given result
    * @group Transformations
    */
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): Expect[T] =
    val newDefaultValue = flatMapPF.andThen(_.defaultValue).orElse(mapPF)
      .applyOrElse(defaultValue, notDefined("transform", "neither flatMapPF nor mapPF are defined at the Expect default value"))
    
    new Expect[T](command, newDefaultValue, settings)(expectBlocks.map(_.transform(flatMapPF, mapPF))*)
  
  /**
    * Zips the results of `this` and `that` Expect, and creates a new Expect holding the tuple of their results.
    *
    * @tparam T the type of the returned Expect
    * @param that the other Expect
    * @return an Expect with the results of both Expects
    * @group Transformations
    */
  def zip[T](that: Expect[T]): Expect[(R, T)] = zipWith(that)((r1, r2) => (r1, r2))
  
  /**
    * Zips the results of `this` and `that` Expect using a function `f`,
    *  and creates a new Expect holding the result.
    *
    * @tparam T the type of the other Expect
    * @tparam U the type of the resulting Expect
    * @param that the other Expect
    * @param f the function to apply to the results of `this` and `that`
    * @return an Expect with the result of the application of `f` to the results of `this` and `that`
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
    * Returns whether `other` Expect has the same:
    *  - command
    *  - defaultValue
    *  - settings
    *  - expectBlocks
    * as this Expect.
    *
    * If any `expectBlock` contains an Action with a function, eg. Returning, this method will return false,
    * because equality is not defined for functions.
    *
    * The method `structurallyEqual` can be used to test whether two expects have the same structure.
    *
    * @param other the other Expect to compare this Expect to.
    */
  override def equals(obj: Any): Boolean = obj match
    case e @ Expect(`command`, `defaultValue`, `settings`) if e.expectBlocks == expectBlocks => true
    case _ => false
  
  /**
    * Returns whether the `other` Expect has structurally the same:
    *  - command
    *  - defaultvalue
    *  - settings
    *  - expectBlocks (compared structurally)
    * as this Expect.
    * @param other the other Expect to compare this Expect to.
    */
  def structurallyEquals[RR >: R](other: Expect[RR]): Boolean =
    commandSeq == other.commandSeq &&
      defaultValue == other.defaultValue &&
      settings == other.settings &&
      expectBlocks.size == other.expectBlocks.size &&
      expectBlocks.zip(other.expectBlocks).forall{ case (a, b) => a.structurallyEquals(b) }
  
  override def hashCode(): Int =
    Seq(command, defaultValue, settings, expectBlocks)
      .map(_.hashCode())
      .foldLeft(0)((a, b) => 31 * a + b)