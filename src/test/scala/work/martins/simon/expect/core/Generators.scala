package work.martins.simon.expect.core

import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Gen._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import work.martins.simon.expect.core.actions._

import scala.annotation.tailrec
import scala.util.matching.Regex

trait Generators extends GeneratorDrivenPropertyChecks {

  def simpleLs[R](result: R): Expect[R] = new Expect("ls", result)(
    new ExpectBlock(
      new StringWhen("README")(
        Returning {
          result
        }
      )
    )
  )

  def genExit[R]: Gen[Exit[R]] = const(new Exit[R])
  def genSend[R]: Gen[Send[R]] = arbitrary[String].map(s => new Send[R](s))
  def genSendWithRegex[R]: Gen[SendWithRegex[R]] = arbitrary[String].map(s => new SendWithRegex[R](m => s))
  def genReturning[R](result: R, builderAction: => Unit): Gen[Returning[R]] = {
    const(new Returning[R]({ _ =>
      builderAction
      result
    }))
  }
  def genReturningWithRegex[R](result: R, builderAction: => Unit): Gen[ReturningWithRegex[R]] = {
    const(new ReturningWithRegex[R]({ _ =>
      builderAction
      result
    }))
  }
  def genReturningExpect[R](result: R, builderAction: => Unit): Gen[ReturningExpect[R]] = {
    const(new ReturningExpect[R]({ _ =>
      builderAction
      simpleLs(result)
    }))
  }
  def genReturningExpectWithRegex[R](result: R, builderAction: => Unit): Gen[ReturningExpectWithRegex[R]] = {
    const(new ReturningExpectWithRegex[R]({ _ =>
      builderAction
      simpleLs(result)
    }))
  }

  def genReturningAction[R](result: R, builderAction: => Unit): Gen[Action[R, When]] = oneOf(
    genReturning(result, builderAction),
    genReturningExpect(result, builderAction)
  )
  def genReturningRegexAction[R](result: R, builderAction: => Unit): Gen[Action[R, RegexWhen]] = oneOf(
    genReturningWithRegex(result, builderAction),
    genReturningExpectWithRegex(result, builderAction)
  )

  def genAction[R](result: R, builderAction: => Unit, interactive: Boolean = false): Gen[Action[R, When]] = {
    val base = oneOf(
      genExit[R],
      genReturningAction(result, builderAction)
    )

    if (interactive) {
      oneOf(base, genSend[R])
    } else {
      base
    }
  }
  def genRegexAction[R](result: R, builderAction: => Unit, interactive: Boolean = false): Gen[Action[R, RegexWhen]] = {
    val base = oneOf(
      genAction(result, builderAction, interactive),
      genReturningRegexAction[R](result, builderAction)
    )

    if (interactive) {
      oneOf(base, genSendWithRegex[R])
    } else {
      base
    }
  }

  def genStringWhen[R](pattern: String, result: R, builderAction: => Unit, interactive: Boolean = false): Gen[StringWhen[R]] = {
    listOf(genAction(result, builderAction, interactive)).map(a => new StringWhen[R](pattern)(a:_*))
  }
  def genRegexWhen[R](pattern: Regex, result: R, builderAction: => Unit, interactive: Boolean = false): Gen[RegexWhen[R]] = {
    listOf(genAction(result, builderAction, interactive)).map(a => new RegexWhen[R](pattern)(a:_*))
  }
  def genEndOfFileWhen[R](result: R, builderAction: => Unit, interactive: Boolean = false): Gen[EndOfFileWhen[R]] = {
    listOf(genAction(result, builderAction, interactive)).map(a => new EndOfFileWhen[R](a:_*))
  }
  def genTimeoutWhen[R](result: R, builderAction: => Unit, interactive: Boolean = false): Gen[TimeoutWhen[R]] = {
    listOf(genAction(result, builderAction, interactive)).map(a => new TimeoutWhen[R](a:_*))
  }
  def genWhen[R](pattern: String, result: R, builderAction: => Unit, interactive: Boolean = false): Gen[When[R]] = oneOf(
    genStringWhen(pattern, result, builderAction, interactive),
    genRegexWhen(pattern.r, result, builderAction, interactive),
    genEndOfFileWhen(result, builderAction, interactive)
    //genTimeoutWhen(result, builderAction, interactive)
  )


  /**
    * Generates an Expect with a single ExpectBlock, with a single When, with multiple Actions.
    */
  def genSingleExpectBlockWhenMultipleActionExpect[R](implicit a: Arbitrary[R]): Gen[(Expect[R], StringBuilder, String, R)] = for {
    defaultValue <- arbitrary[R]
    result <- arbitrary[R].suchThat(_ != defaultValue)
    builder = new StringBuilder("")
    addToBuilder <- alphaStr.suchThat(_.nonEmpty)
    when <- genWhen("README", result, builder.append(addToBuilder), interactive = false)//ls is not interactive
    expect = new Expect("ls", defaultValue)(new ExpectBlock(when))
  } yield (expect, builder, addToBuilder, result)


  def numberOfReturningsToAnExit[R](expect: Expect[R]): Int = {
    expect.expectBlocks.flatMap(_.whens.map { when =>
      @tailrec
      def countReturnings(actions: Seq[Action[R, when.This]], count: Int): Int = actions match {
        case Seq(_: Exit[R], _*) => count
        case Seq((_: ReturningExpect[R] | _: ReturningExpectWithRegex[R]), _*) => count + 1
        case Seq((_: Returning[R] | _: ReturningWithRegex[R]), tail@_*) => countReturnings(tail, count + 1)
        case Seq(_, tail@_*) => countReturnings(tail, count)
        case _/*Seq()*/ => count
      }
      countReturnings(when.actions, 0)
    }).headOption.getOrElse(0)
  }
}
