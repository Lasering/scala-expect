package work.martins.simon.expect.core

import scala.util.matching.Regex
import scala.util.matching.Regex.Match
import work.martins.simon.expect./=>
import work.martins.simon.expect.StringUtils.*
import work.martins.simon.expect.core.actions.Action
import work.martins.simon.expect.{EndOfFile, FromInputStream, Timeout}
import work.martins.simon.expect.FromInputStream.StdOut
import work.martins.simon.expect.core.ExecutionAction.*

object When:
  def apply[R](pattern: String)(actions: Action[R, StringWhen]*): StringWhen[R] =
    StringWhen(pattern, actions = actions*)
  def apply[R](pattern: String, readFrom: FromInputStream)(actions: Action[R, StringWhen]*): StringWhen[R] =
    StringWhen(pattern, readFrom, actions*)
  
  def apply[R](pattern: Regex)(actions: Action[R, RegexWhen]*): RegexWhen[R] =
    RegexWhen(pattern, actions = actions*)
  def apply[R](pattern: Regex, readFrom: FromInputStream)(actions: Action[R, RegexWhen]*): RegexWhen[R] =
    RegexWhen(pattern, readFrom, actions*)
  
  // Pattern is not used but we need it to disambiguate between creating a EndOfFileWhen vs a TimeoutWhen.
  def apply[R](pattern: EndOfFile.type, readFrom: FromInputStream = StdOut)(actions: Action[R, EndOfFileWhen]*): EndOfFileWhen[R] =
    EndOfFileWhen(readFrom, actions*)
  
  def apply[R](pattern: Timeout.type)(actions: Action[R, TimeoutWhen]*): TimeoutWhen[R] =
    TimeoutWhen(actions*)

sealed trait When[+R]:
  /** The concrete When type constructor to which the actions will be applied. */
  type This[+X] <: When[X]
  
  /** From which InputStream to read text from. */
  def readFrom: FromInputStream
  
  /** The actions this when runs when it matches against the output read from `readFrom` InputStream. */
  def actions: Seq[Action[R, This]]
  
  /**
    * @param output the String to match against.
    * @return whether this When matches against `output`.
    */
  def matches(output: String): Boolean = false
  
  /**
    * @param output the output to trim.
    * @return the text in `output` that remains after removing all the text up to and including the first occurrence
    *         of the text matched by this When.
    */
  def trimToMatchedText(output: String): String = output
  
  /** Runs all the actions of this When. */
  private[core] def run[RR >: R](runContext: RunContext[RR]): RunContext[RR] =
    import work.martins.simon.expect.core.ExecutionAction.*
    import scala.annotation.tailrec
    
    @tailrec
    def runInner(actions: Seq[Action[RR, This]], innerRunContext: RunContext[RR]): RunContext[RR] =
      actions.headOption match
        case Some(action) =>
          val newRunContext = action.run(this.asInstanceOf[This[RR]], innerRunContext)
          newRunContext.executionAction match
            case Continue =>
              runInner(actions.tail, newRunContext)
            case Terminate | ChangeToNewExpect(_) =>
              // We just return the new run context to do a preemptive exit, ie, ensure
              // anything after this action does not get executed
              newRunContext
        case None =>
          // No more actions. We just return the current RunContext
          innerRunContext
    
    runInner(actions, runContext).withOutput(trimToMatchedText)
  
  def map[T](f: R => T): This[T] = withActions(actions.map(_.map(f)))
  def flatMap[T](f: R => Expect[T]): This[T] = withActions(actions.map(_.flatMap(f)))
  def transform[T](flatMapPF: R /=> Expect[T], mapPF: R /=> T): This[T] = withActions(actions.map(_.transform(flatMapPF, mapPF)))
  
  /** Create a new When with the specified actions. */
  def withActions[T](actions: Seq[Action[T, This]]): This[T]
  
  /**
    * Returns whether the `other` When has structurally the same actions as this When.
    * @param other the other When to compare this When to.
    */
  def structurallyEquals[RR >: R](other: When[RR]): Boolean =
    readFrom == other.readFrom && actions.size == other.actions.size &&
      actions.zip(other.actions).forall(_.structurallyEquals(_))
  
  protected def toString(pattern: String): String =
    s"""when($pattern, readFrom = $readFrom) {
       |${actions.mkString("\n").indent()}
       |}""".stripMargin

final case class StringWhen[+R](pattern: String, readFrom: FromInputStream = StdOut, actions: Action[R, StringWhen]*) extends When[R] derives CanEqual:
  type This[+X] = StringWhen[X]
  
  override def matches(output: String): Boolean = output.contains(pattern)
  
  override def trimToMatchedText(output: String): String = output.substring(output.indexOf(pattern) + pattern.length)
  
  def withActions[T](actions: Seq[Action[T, This]]): StringWhen[T] = StringWhen(pattern, readFrom, actions*)
  
  override def toString: String = toString(escape(pattern))
  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match
    case that: StringWhen[RR] => pattern == that.pattern && super.structurallyEquals(other)
    case _ => false

final case class RegexWhen[+R](pattern: Regex, readFrom: FromInputStream = StdOut, actions: Action[R, RegexWhen]*) extends When[R] derives CanEqual:
  type This[+X] = RegexWhen[X]
  
  override def matches(output: String): Boolean = pattern.findFirstIn(output).isDefined
  
  override def trimToMatchedText(output: String): String = output.substring(regexMatch(output).end(0))
  
  protected[core] def regexMatch(output: String): Match =
    //We have the guarantee that .get will be successful because this method
    //is only invoked if `matches` returned true.
    pattern.findFirstMatchIn(output).get
  
  def withActions[T](actions: Seq[Action[T, This]]): RegexWhen[T] = RegexWhen(pattern, readFrom, actions *)
  
  override def toString: String = toString(escape(pattern.regex) + ".r")
  override def equals(other: Any): Boolean = other.asInstanceOf[Matchable] match
    // equals on the Regex class is not defined.
    case that: RegexWhen[?] => pattern.regex == that.pattern.regex && readFrom == that.readFrom && actions == that.actions
    case _ => false
  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match
    case that: RegexWhen[RR] => pattern.regex == that.pattern.regex && super.structurallyEquals(other)
    case _ => false

final case class EndOfFileWhen[+R](readFrom: FromInputStream = StdOut, actions: Action[R, EndOfFileWhen]*) extends When[R] derives CanEqual:
  type This[+X] = EndOfFileWhen[X]
  
  def withActions[T](actions: Seq[Action[T, This]]): EndOfFileWhen[T] = EndOfFileWhen(readFrom, actions *)
  
  override def toString: String = toString("EndOfFile")
  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match
    case _: EndOfFileWhen[RR] => super.structurallyEquals(other)
    case _ => false

final case class TimeoutWhen[+R](actions: Action[R, TimeoutWhen]*) extends When[R] derives CanEqual:
  type This[+X] = TimeoutWhen[X]
  
  /** The readFrom of a TimeoutWhen is not used but to keep the implementation simple we set its value to StdOut. */
  val readFrom: FromInputStream = StdOut
  
  def withActions[T](actions: Seq[Action[T, This]]): TimeoutWhen[T] = TimeoutWhen(actions *)
  
  override def toString: String =
    s"""when(Timeout) {
       |${actions.mkString("\n").indent()}
       |}""".stripMargin
  override def structurallyEquals[RR >: R](other: When[RR]): Boolean = other match
    case _: TimeoutWhen[RR] => super.structurallyEquals(other)
    case _ => false