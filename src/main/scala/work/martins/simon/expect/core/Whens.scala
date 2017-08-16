package work.martins.simon.expect.core

import com.typesafe.scalalogging.LazyLogging
import scala.language.higherKinds

import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core.actions.Action
import scala.annotation.tailrec
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

import work.martins.simon.expect.core.RunContext.{ChangeToNewExpect, Continue, Terminate}
import work.martins.simon.expect.{FromInputStream, StdOut}

/**
  * @define type `When`
  */
sealed trait When[+R] extends LazyLogging {
  /** The concrete $type type constructor to which the actions will be applied. */
  type This[X] <: When[X]

  // Because a core When does not have access to its parent (an ExpectBlock) we cannot implement the same
  // strategy of the fluent When, which uses by default for the `readFrom` its parent `readFrom`.
  // We could add the parent to the constructor of the when but that would make the core When unusable.
  /** From which InputStream to read text from. */
  def readFrom: FromInputStream
  
  /** The actions this $type runs when it matches against the output read from `readFrom` InputStream. */
  def actions: Seq[Action[R, This]]

  /**
   * @param output the String to match against.
   * @return whether this $type matches against `output`.
   */
  def matches(output: String): Boolean

  /**
   * @param output the output to trim.
   * @return the text in `output` that remains after removing all the text up to and including the first occurrence
    *        of the text matched by this $type.
   */
  def trimToMatchedText(output: String): String = output

  /** Runs all the actions of this $type. */
  def run[RR >: R](runContext: RunContext[RR]): RunContext[RR] = {
    @tailrec
    def runInner(actions: Seq[Action[RR, This]], innerRunContext: RunContext[RR]): RunContext[RR] = {
      actions.headOption match {
        case Some(action) =>
          val newRunContext = action.run(this.asInstanceOf[This[RR]], innerRunContext)
          newRunContext.executionAction match {
            case Continue =>
              runInner(actions.tail, newRunContext)
            case Terminate | ChangeToNewExpect(_) =>
              // We just return the new run context to do a preemptive exit, ie, ensure
              // anything after this action does not get executed
              newRunContext
          }
        case None =>
          // No more actions. We just return the current RunContext
          innerRunContext
      }
    }

    runInner(actions, runContext).withOutput(trimToMatchedText)
  }

  private[core] def map[T](f: R => T): This[T] = withActions(actions.map(_.map(f)))
  private[core] def flatMap[T](f: R => Expect[T]): This[T] = withActions(actions.map(_.flatMap(f)))
  private[core] def transform[T](flatMapPF: PartialFunction[R, Expect[T]], mapPF: PartialFunction[R, T]): This[T] = {
    withActions(actions.map(_.transform(flatMapPF, mapPF)))
  }

  /** Create a new $type with the specified actions. */
  def withActions[T](actions: Seq[Action[T, This]]): This[T]

  /**
    * @define subtypes actions
    * Returns whether the other $type has the same number of $subtypes as this $type and
    * that each pair of $subtypes is structurally equal.
    *
    * @param other the other $type to compare this $type to.
    */
  def structurallyEquals[RR >: R](other: When[RR]): Boolean = {
    actions.size == other.actions.size && actions.zip(other.actions).forall { case (a, b) => a.structurallyEquals(b) } &&
    readFrom == other.readFrom
  }

  /** Textual representation of the pattern this $type will match against. */
  def patternString: String
  
  override def toString: String =
    s"""when($patternString, readFrom = $readFrom) {
       |${actions.mkString("\n").indent()}
       |}""".stripMargin
}

case class StringWhen[R](pattern: String, readFrom: FromInputStream = StdOut)(val actions: Action[R, StringWhen]*) extends When[R] {
  final type This[X] = StringWhen[X]

  override def matches(output: String): Boolean = output.contains(pattern)
  override def trimToMatchedText(output: String): String = {
    output.substring(output.indexOf(pattern) + pattern.length)
  }

  def withActions[T](actions: Seq[Action[T, This]]): StringWhen[T] = StringWhen(pattern, readFrom)(actions:_*)

  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match {
    case that: StringWhen[R] => super.structurallyEquals(other) && pattern == that.pattern
    case _ => false
  }

  override def equals(other: Any): Boolean = other match {
    case that: StringWhen[R] => pattern == that.pattern && actions == that.actions
    case _ => false
  }
  override def hashCode(): Int = Seq(pattern, actions).map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)

  val patternString: String = escape(pattern)
}
case class RegexWhen[R](pattern: Regex, readFrom: FromInputStream = StdOut)(val actions: Action[R, RegexWhen]*) extends When[R] {
  final type This[X] = RegexWhen[X]

  override def matches(output: String): Boolean = pattern.findFirstIn(output).isDefined
  override def trimToMatchedText(output: String): String = output.substring(regexMatch(output).end(0))

  protected[core] def regexMatch(output: String): Match = {
    //We have the guarantee that .get will be successful because this method
    //is only invoked if `matches` returned true.
    pattern.findFirstMatchIn(output).get
  }

  def withActions[T](actions: Seq[Action[T, This]]): RegexWhen[T] = RegexWhen(pattern, readFrom)(actions:_*)
  
  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match {
    case that: RegexWhen[R] => super.structurallyEquals(other) && pattern.regex == that.pattern.regex
    case _ => false
  }

  override def equals(other: Any): Boolean = other match {
    case that: RegexWhen[R] => pattern.regex == that.pattern.regex && actions == that.actions
    case _ => false
  }
  override def hashCode(): Int = Seq(pattern, actions).map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)

  val patternString: String = escape(pattern.regex) + ".r"
}

// EndOfFileWhen and TimeoutWhen are special whens because they will only be used inside the same ExpectBlock:
// In this case:
//    ExpectBlock (
//      StringWhen(...)(
//        ...
//      ),
//      TimeoutWhen(...)(
//        ...
//      )
//    )
// If a timeout is thrown while trying to read text for the StringWhen then the actions of the TimeoutWhen will be
// executed. However in this case (considering the expect block with the StringWhen also times out):
//    ExpectBlock (
//      StringWhen(...)(
//        ...
//      )
//    ), ExpectBlock(
//      TimeoutWhen(...)(
//        ...
//      )
//    )
// The TimeoutWhen is useless since the ExpectBlock with the StringWhen will timeout and without having a TimeoutWhen
// declared in its whens will throw a TimeoutExpect causing the expect to terminate and therefor the next expect block
// (the one with the timeout when) will never be executed.
// TODO: explain this caveat in their scaladoc

case class EndOfFileWhen[R](readFrom: FromInputStream = StdOut)(val actions: Action[R, EndOfFileWhen]*) extends When[R] {
  final type This[X] = EndOfFileWhen[X]

  override def matches(output: String) = false

  def withActions[T](actions: Seq[Action[T, This]]): EndOfFileWhen[T] = EndOfFileWhen(readFrom)(actions:_*)
  
  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match {
    case _: EndOfFileWhen[R] => super.structurallyEquals(other)
    case _ => false
  }

  val patternString: String = "EndOfFile"
}
case class TimeoutWhen[R]()(val actions: Action[R, TimeoutWhen]*) extends When[R] {
  final type This[X] = TimeoutWhen[X]

  override def matches(output: String) = false

  // The readFrom of a TimeoutWhen is not used but to keep the implementation simple we also include it
  // and set its value to StdOut, which is of no consequence since its not used.
  final def readFrom: FromInputStream = StdOut
  
  def withActions[T](actions: Seq[Action[T, This]]): TimeoutWhen[T] = TimeoutWhen()(actions:_*)
  
  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match {
    case _: TimeoutWhen[R] => super.structurallyEquals(other)
    case _ => false
  }

  val patternString: String = "Timeout"
}