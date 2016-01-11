# Scala-expect [![Build Status](https://travis-ci.org/Lasering/scala-expect.svg)](https://travis-ci.org/Lasering/scala-expect) [![Codacy Badge](https://api.codacy.com/project/badge/74ba0150f4034c8294e66f6b97a2f69f)](https://www.codacy.com/app/lasering/scala-expect)

A Scala implementation of a very small subset of the widely known TCL expect.

Scala-expect comes with three different flavors: `core`, `fluent` and `dsl`.
<!---
## Get scala-expect
```scala
libraryDependencies += "work.martins.simon" %% "scala-expect" % "1.7.1"
```
--->

## Core
#### Advantages
* Closer to the metal / The basis for the other flavors.
* Immutable and therefore thread-safe.
* Most errors will be caught in compile time.

#### Disadvantages
* Verbose syntax.
* Can't cleanly add expect blocks/whens/actions based on a condition.

#### Example
```scala
import work.martins.simon.core._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex.Match

val e = new Expect("bc -i", defaultValue = 5)(
  new ExpectBlock(
    new StringWhen("For details type `warranty'.")(
      Sendln("1 + 2")
    )
  ),
  new ExpectBlock(
    new RegexWhen("""(?m)^(\d+)$""".r)(
      SendlnWithRegex { m: Match =>
        val previousAnswer = m.group(1)
        println(s"Got $previousAnswer")
        s"$previousAnswer + 3"
      }
    )
  ),
  new ExpectBlock(
	new RegexWhen("""(?m)^(\d+)$""".r)(
	  ReturningWithRegex(_.group(1).toInt)
	)
  )
)
e.run() //Should return 6 inside a Future[Int]
```

## Fluent
#### Advantages
* Less verbose syntax.
* Most errors will be caught in compile time.
* Easy to add expect blocks/whens/actions based on a condition.
* Easy to refactor the creation of expects.

#### Disadvantages
* Some overhead since the fluent expect is just a builder for a core expect.
* Mutable - the fluent expect has to maintain a state of the objects that have been built.
* IDE's will easily screw the custom indentation.

#### Example
```scala
import work.martins.simon.fluent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex.Match

val e = new Expect("bc -i", defaultValue = 5)
e.expect
  .when("For details type `warranty'.")
    .sendln("1 + 2")
e.expect
  .when("""(?m)^(\d+)$""".r)
    .sendln { m: Match =>
      val previousAnswer = m.group(1)
      println(s"Got $previousAnswer")
      s"$previousAnswer + 3"
    }
//This is a shortcut. It works just like the previous expect block.
e.expect("""(?m)^(\d+)$""".r)
  .returning(_.group(1).toInt)
e.run() //Should return 6 inside a Future[Int]
```

## DSL
#### Advantages
* Code will be indented in blocks so IDE's won't screw the indentation.
* Syntax more close to the TCL expect.
* Easy to add expect blocks/whens/actions based on a condition.
* Easy to refactor the creation of expects.

#### Disadvantages
* Most errors will only be caught in runtime as oposed to compile time.
* More overhead than the fluent expect since it's just a wrapper arround fluent expect.
* Mutable - it uses a fluent expect as the backing expect and a mutable stack to keep track of the current context.

#### Example
```scala
import work.martins.simon.dsl._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex.Match

val e = new Expect("bc -i", defaultValue = 5) {
  expect {
    when("For details type `warranty'.") {
      sendln("1 + 2")
    }
  }
  expect {
    when("""(?m)^(\d+)$""".r) {
      sendln { m: Match =>
        val previousAnswer = m.group(1)
        println(s"Got $previousAnswer")
        s"$previousAnswer + 3"
      }
    }
  }
  //This is a shortcut. It works just like the previous expect block.
  expect("""(?m)^(\d+)$""".r) { 
    returning(_.group(1).toInt)
  }
}
e.run() //Should return 6 inside a Future[Int]
```
