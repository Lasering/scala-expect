package work.martins.simon.expect.core

import scala.annotation.tailrec
import scala.util.matching.Regex
import com.typesafe.scalalogging.LazyLogging
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core.RunContext.{ChangeToNewExpect, Continue, Terminate}
import work.martins.simon.expect.core.actions.Action
import work.martins.simon.expect.{EndOfFile, FromInputStream, StdOut, Timeout}

object When {
  /*
  // This code is cleaner, but IDEs have trouble performing code completion.
  trait WhenBuilder[PT, R, W[+X] <: When[X]] {
    def build(s: PT, f: FromInputStream): Seq[Action[R, W]] => W[R]
  }
  
  // SAMs for the win!
  implicit def stringWhenBuilder[R]: WhenBuilder[String, R, StringWhen] = StringWhen.apply[R]
  implicit def regexWhenBuilder[R]: WhenBuilder[Regex, R, RegexWhen] = RegexWhen.apply[R]
  implicit def endOfFileWhenBuilder[R]: WhenBuilder[EndOfFile.type, R, EndOfFileWhen] = (_, from) => EndOfFileWhen[R](from)
  implicit def timeoutWhenBuilder[R]: WhenBuilder[Timeout.type, R, TimeoutWhen] = (_, _) => TimeoutWhen.apply[R]()
  
  def apply[PT, R, W[+X] <: When[X]](pattern: PT, readFrom: FromInputStream = StdOut)
                                   (actions: Action[R, W]*)(implicit whenBuilder: WhenBuilder[PT, R, W]): W[R] = {
    whenBuilder.build(pattern, readFrom)(actions)
  }
  */
  
  def apply[R](pattern: String)(actions: Action[R, StringWhen]*): StringWhen[R] = StringWhen(pattern)(actions:_*)
  def apply[R](pattern: String, readFrom: FromInputStream)(actions: Action[R, StringWhen]*): StringWhen[R] = {
    StringWhen(pattern, readFrom)(actions:_*)
  }
  
  def apply[R](pattern: Regex)(actions: Action[R, RegexWhen]*): RegexWhen[R] = RegexWhen(pattern)(actions:_*)
  def apply[R](pattern: Regex, readFrom: FromInputStream)(actions: Action[R, RegexWhen]*): RegexWhen[R] = {
    RegexWhen(pattern, readFrom)(actions:_*)
  }
  
  import com.github.ghik.silencer.silent
  // the pattern is not used but we need it to disambiguate between creating a EndOfFileWhen vs a TimeoutWhen. One could say its an erased term.
  def apply[R](@silent pattern: EndOfFile.type, readFrom: FromInputStream = StdOut)(actions: Action[R, EndOfFileWhen]*): EndOfFileWhen[R] = {
    EndOfFileWhen(readFrom)(actions:_*)
  }
  def apply[R](@silent pattern: Timeout.type)(actions: Action[R, TimeoutWhen]*): TimeoutWhen[R] = TimeoutWhen()(actions:_*)
}

/**
  * @define type `When`
  */
sealed trait When[+R] extends LazyLogging {
  /** The concrete $type type constructor to which the actions will be applied. */
  type This[+X] <: When[X]

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
  private[core] def run[RR >: R](runContext: RunContext[RR]): RunContext[RR] = {
    @tailrec
    def runInner(actions: Seq[Action[RR, This]], innerRunContext: RunContext[RR]): RunContext[RR] =
      actions.headOption match {
        case None => innerRunContext
        case Some(action) =>
          val newRunContext = action.run(this.asInstanceOf[This[RR]], innerRunContext)
          newRunContext.executionAction match {
            case Continue => runInner(actions.tail, newRunContext)
            // We just return the new run context to do a preemptive exit, ie, ensure
            // anything after this action does not get executed
            case Terminate | ChangeToNewExpect(_) => newRunContext
          }
      }

    runInner(actions, runContext).withOutput(trimToMatchedText)
  }

  def map[T](f: R => T): This[T] = withActions(actions.map(_.map(f)))
  def flatMap[T](f: R => Expect[T]): This[T] = withActions(actions.map(_.flatMap(f)))
  def transform[T](flatMapPF: PartialFunction[R, Expect[T]], mapPF: PartialFunction[R, T]): This[T] = {
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

  protected def toString(pattern: String): String = {
    s"""when($pattern, readFrom = $readFrom) {
       |${actions.mkString("\n").indent()}
       |}""".stripMargin
  }
}

final case class StringWhen[+R](pattern: String, readFrom: FromInputStream = StdOut)(val actions: Action[R, StringWhen]*) extends When[R] {
  type This[+X] = StringWhen[X]

  override def matches(output: String): Boolean = output.contains(pattern)
  override def trimToMatchedText(output: String): String = {
    output.substring(output.indexOf(pattern) + pattern.length)
  }

  def withActions[T](actions: Seq[Action[T, This]]): StringWhen[T] = StringWhen(pattern, readFrom)(actions:_*)

  override def toString: String = toString(escape(pattern))
  override def equals(other: Any): Boolean = other match {
    case that: StringWhen[R] => pattern == that.pattern && readFrom == that.readFrom && actions == that.actions
    case _ => false
  }
  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match {
    case that: StringWhen[R] => pattern == that.pattern && super.structurallyEquals(other)
    case _ => false
  }
  override def hashCode(): Int = {
    Seq(pattern, readFrom, actions)
      .map(_.hashCode())
      .foldLeft(0)((a, b) => 31 * a + b)
  }
}
final case class RegexWhen[+R](pattern: Regex, readFrom: FromInputStream = StdOut)(val actions: Action[R, RegexWhen]*) extends When[R] {
  type This[+X] = RegexWhen[X]
  
  import scala.util.matching.Regex.Match
  
  override def matches(output: String): Boolean = pattern.findFirstIn(output).isDefined
  override def trimToMatchedText(output: String): String = output.substring(regexMatch(output).end(0))

  protected[core] def regexMatch(output: String): Match = {
    //We have the guarantee that .get will be successful because this method
    //is only invoked if `matches` returned true.
    pattern.findFirstMatchIn(output).get
  }

  def withActions[T](actions: Seq[Action[T, This]]): RegexWhen[T] = RegexWhen(pattern, readFrom)(actions:_*)

  override def toString: String = toString(escape(pattern.regex) + ".r")
  override def equals(other: Any): Boolean = other match {
    case that: RegexWhen[R] => pattern.regex == that.pattern.regex && readFrom == that.readFrom && actions == that.actions
    case _ => false
  }
  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match {
    case that: RegexWhen[R] => pattern.regex == that.pattern.regex && super.structurallyEquals(other)
    case _ => false
  }
  override def hashCode(): Int = {
    Seq(pattern, readFrom, actions)
      .map(_.hashCode())
      .foldLeft(0)((a, b) => 31 * a + b)
  }
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

final case class EndOfFileWhen[+R](readFrom: FromInputStream = StdOut)(val actions: Action[R, EndOfFileWhen]*) extends When[R] {
  type This[+X] = EndOfFileWhen[X]

  override def matches(output: String): Boolean = false

  def withActions[T](actions: Seq[Action[T, This]]): EndOfFileWhen[T] = EndOfFileWhen(readFrom)(actions:_*)
  
  override def toString: String = toString("EndOfFile")
  override def equals(other: Any): Boolean = other match {
    case that: EndOfFileWhen[R] => readFrom == that.readFrom && actions == that.actions
    case _ => false
  }
  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match {
    case _: EndOfFileWhen[R] => super.structurallyEquals(other)
    case _ => false
  }
  override def hashCode(): Int = {
    Seq(readFrom, actions)
      .map(_.hashCode())
      .foldLeft(0)((a, b) => 31 * a + b)
  }
}
final case class TimeoutWhen[+R]()(val actions: Action[R, TimeoutWhen]*) extends When[R] {
  type This[+X] = TimeoutWhen[X]

  override def matches(output: String): Boolean = false
  
  /** The readFrom of a TimeoutWhen is not used but to keep the implementation simple we set its value to StdOut. */
  val readFrom: FromInputStream = StdOut
  
  def withActions[T](actions: Seq[Action[T, This]]): TimeoutWhen[T] = TimeoutWhen()(actions:_*)
  
  override def toString: String = s"""when(Timeout) {
                                     |${actions.mkString("\n").indent()}
                                     |}""".stripMargin
  override def equals(other: Any): Boolean = other match {
    case that: TimeoutWhen[R] => actions == that.actions
    case _ => false
  }
  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match {
    case _: TimeoutWhen[R] => super.structurallyEquals(other)
    case _ => false
  }
  override def hashCode(): Int = actions.hashCode()
}
