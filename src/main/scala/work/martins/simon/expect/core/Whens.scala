package work.martins.simon.expect.core

import com.typesafe.scalalogging.LazyLogging

import scala.language.higherKinds
import work.martins.simon.expect.StringUtils._
import work.martins.simon.expect.core.actions.Action

import scala.annotation.tailrec
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

/**
  * @define type when
  */
sealed trait When[R] extends LazyLogging {
  /** The concrete When type constructor to which the actions will be applied. */
  type This[X] <: When[X]

  /** The actions this $type runs when it matches against the current output of Expect. */
  def actions: Seq[Action[R, This]]

  /**
   * @param output the String to match against.
   * @return whether this When matches against `output`.
   */
  def matches(output: String): Boolean = false
  /**
   * @param output the output to trim.
   * @return the text in `output` that remains after removing all the text up to the first occurrence of the text
   *         matched by this When and then removing the text matched by this When.
   */
  def trimToMatchedText(output: String): String = output

  /**
   * Executes all the actions of this When.
   */
  def execute(process: RichProcess, intermediateResult: IntermediateResult[R]): IntermediateResult[R] = {
    @tailrec
    def executeInner(actions: Seq[Action[R, This]], result: IntermediateResult[R]): IntermediateResult[R] = {
      actions.headOption match {
        case Some(action) =>
          val ir = action.execute(this.asInstanceOf[This[R]], process, result)
          ir.executionAction match {
            case Continue =>
              //Continue with the remaining actions
              executeInner(actions.tail, ir)
            case Terminate | ChangeToNewExpect(_) =>
              //We just return ir because we want do a preemptive exit
              //ie, ensure anything after this action does not get executed)
              ir
          }
        case None =>
          //No more actions. We just return the current intermediateResult
          result
      }
    }

    val finalResult = executeInner(actions, intermediateResult)
    finalResult.copy(output = trimToMatchedText(finalResult.output))
  }

  private[core] def map[T](f: R => T): This[T] = withActions(actions.map(_.map(f)))
  private[core] def flatMap[T](f: R => Expect[T]): This[T] = withActions(actions.map(_.flatMap(f)))
  private[core] def transform[T](mapPF: PartialFunction[R, T])(flatMapPF: PartialFunction[R, Expect[T]]): This[T] = {
    withActions(actions.map(_.transform(mapPF)(flatMapPF)))
  }

  /** Create a new $type with the specified actions. */
  def withActions[T](actions: Seq[Action[T, This]]): This[T]

  protected def structurallyEqualActions(other: When[R]): Boolean = {
    actions.size == other.actions.size && actions.zip(other.actions).forall { case (a, b) => a.structurallyEquals(b) }
  }

  /**
    * @define subtypes actions
    * Returns whether the other $type has the same number of $subtypes as this $type and
    * that each pair of $subtypes is structurally equal.
    *
    * @param other the other $type to campare this $type to.
    */
  def structurallyEquals(other: When[R]): Boolean

  def patternString: String
  override def toString: String =
    s"""when($patternString) {
       |${actions.mkString("\n").indent()}
       |}""".stripMargin
}

case class StringWhen[R](pattern: String)(val actions: Action[R, StringWhen]*) extends When[R] {
  final type This[X] = StringWhen[X]

  override def matches(output: String): Boolean = output.contains(pattern)
  override def trimToMatchedText(output: String): String = {
    output.substring(output.indexOf(pattern) + pattern.length)
  }

  def withActions[T](actions: Seq[Action[T, This]]): StringWhen[T] = StringWhen(pattern)(actions:_*)

  override def structurallyEquals(other: When[R]): Boolean = other match {
    case that: StringWhen[R] => structurallyEqualActions(other) && pattern == that.pattern
    case _ => false
  }

  override def equals(other: Any): Boolean = other match {
    case that: StringWhen[R] => pattern == that.pattern && actions == that.actions
    case _ => false
  }
  override def hashCode(): Int = Seq(pattern, actions).map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)

  val patternString: String = escape(pattern)
}
case class RegexWhen[R](pattern: Regex)(val actions: Action[R, RegexWhen]*) extends When[R] {
  final type This[X] = RegexWhen[X]

  override def matches(output: String): Boolean = pattern.findFirstIn(output).isDefined
  override def trimToMatchedText(output: String): String = output.substring(getMatch(output).end(0))

  protected[core] def getMatch(output: String): Match = {
    //We have the guarantee that .get will be successful because this method
    //is only invoked if `matches` returned true.
    pattern.findFirstMatchIn(output).get
  }

  def withActions[T](actions: Seq[Action[T, This]]): RegexWhen[T] = RegexWhen(pattern)(actions:_*)

  override def structurallyEquals(other: When[R]): Boolean = other match {
    case that: RegexWhen[R] => structurallyEqualActions(other) && pattern.regex == that.pattern.regex
    case _ => false
  }

  override def equals(other: Any): Boolean = other match {
    case that: RegexWhen[R] => pattern.regex == that.pattern.regex && actions == that.actions
    case _ => false
  }
  override def hashCode(): Int = Seq(pattern, actions).map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)

  val patternString: String = escape(pattern.regex) + ".r"
}
case class EndOfFileWhen[R](actions: Action[R, EndOfFileWhen]*) extends When[R] {
  final type This[X] = EndOfFileWhen[X]

  def withActions[T](actions: Seq[Action[T, This]]): EndOfFileWhen[T] = EndOfFileWhen(actions:_*)

  def structurallyEquals(other: When[R]): Boolean = other match {
    case that: EndOfFileWhen[R] => structurallyEqualActions(other)
    case _ => false
  }

  val patternString: String = "EndOfFile"
}
case class TimeoutWhen[R](actions: Action[R, TimeoutWhen]*) extends When[R] {
  final type This[X] = TimeoutWhen[X]

  def withActions[T](actions: Seq[Action[T, This]]): TimeoutWhen[T] = TimeoutWhen(actions:_*)

  def structurallyEquals(other: When[R]): Boolean = other match {
    case that: TimeoutWhen[R] => structurallyEqualActions(other)
    case _ => false
  }

  val patternString: String = "Timeout"
}