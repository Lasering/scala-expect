# Scala Expect
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/work.martins.simon/scala-expect_2.11/badge.svg?maxAge=604800)](https://maven-badges.herokuapp.com/maven-central/work.martins.simon/scala-expect_2.11)
[![Dependency Status](https://www.versioneye.com/java/work.martins.simon:scala-expect_2.11/badge.svg?style=plastic&maxAge=604800)](https://www.versioneye.com/java/work.martins.simon:scala-expect_2.11/)
[![Reference Status](https://www.versioneye.com/java/work.martins.simon:scala-expect_2.11/reference_badge.svg?style=plastic&maxAge=604800)](https://www.versioneye.com/java/work.martins.simon:scala-expect_2.11/references)
[![Build Status](https://travis-ci.org/Lasering/scala-expect.svg?branch=master&style=plastic&maxAge=604800)](https://travis-ci.org/Lasering/scala-expect)
[![Codacy Badge](https://api.codacy.com/project/badge/coverage/74ba0150f4034c8294e66f6b97a2f69f)](https://www.codacy.com/app/IST-DSI/scala-expect)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/74ba0150f4034c8294e66f6b97a2f69f)](https://www.codacy.com/app/IST-DSI/scala-expect)
[![Scaladoc](http://javadoc-badge.appspot.com/work.martins.simon/scala-expect_2.11.svg?label=scaladoc&style=plastic&maxAge=604800)](https://Lasering.github.io/scala-expect/latest/api/)
[![license](http://img.shields.io/:license-MIT-blue.svg)](LICENSE)

A Scala implementation of a very small subset of the widely known TCL expect.

Scala Expect comes with three different flavors: `core`, `fluent` and `dsl`.

## Install
```scala
libraryDependencies += "work.martins.simon" %% "scala-expect" % "4.0.0"
```

## Core
#### [Documentation](../../wiki/Core)
#### Advantages
* Closer to metal / basis for the other flavors.
* Immutable and therefore thread-safe.
* Most errors will be caught at compile time (eg. you won't be able to use a `SendWithRegex` inside a `StringWhen`).

#### Disadvantages
* Verbose syntax.
* Can't cleanly add expect blocks/whens/actions based on a condition.

#### Example
```scala
import work.martins.simon.core._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex.Match

val e = new Expect("bc -i", defaultValue = 5)(
  ExpectBlock(
    StringWhen("For details type `warranty'.")(
      Sendln("1 + 2")
    )
  ),
  ExpectBlock(
    RegexWhen("""\n(\d+)\n""".r)(
      SendlnWithRegex { m: Match =>
        val previousAnswer = m.group(1)
        println(s"Got $previousAnswer")
        s"$previousAnswer + 3"
      }
    )
  ),
  ExpectBlock(
	RegexWhen("""\n(\d+)\n""".r)(
	  ReturningWithRegex(_.group(1).toInt)
	)
  )
)
e.run() //Returns 6 inside a Future[Int]
```

## Fluent
#### [Documentation](../../wiki/Fluent)
#### Advantages
* Less verbose syntax:
  * StringWhen, RegexWhen, etc is just `when`.
  * Returning, ReturningWithRegex, etc is just `returning`.
  * Less commas and parenthesis.
* Most errors will be caught at compile time.
* Easy to add expect blocks/whens/actions based on a condition.
* Easy to refactor the creation of expects.
* Can be called from Java easily.

#### Disadvantages
* Some overhead since the fluent expect is just a builder for a core expect.
* Mutable - the fluent expect has to maintain a state of the objects that have been built.
* IDE's will easily mess the custom indentation.

#### Example
```scala
import work.martins.simon.fluent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex.Match

val e = new Expect("bc -i", defaultValue = 5) {
  expect
    .when("For details type `warranty'.")
      .sendln("1 + 2")
  expect
    .when("""\n(\d+)\n""".r)
      .sendln { m: Match =>
        val previousAnswer = m.group(1)
        println(s"Got $previousAnswer")
        s"$previousAnswer + 3"
      }
  //This is a shortcut. It works just like the previous expect block.
  expect("""\n(\d+)\n""".r)
    .returning(_.group(1).toInt)
}
e.run() //Returns 6 inside a Future[Int]
```

## DSL
#### [Documentation](../../wiki/DSL)
#### Advantages
* Code will be indented in blocks so IDE's won't mess the indentation.
* Syntax more close to the TCL expect.
* Easy to add expect blocks/whens/actions based on a condition.
* Easy to refactor the creation of expects.

#### Disadvantages
* Most errors will only be caught at runtime as opposed to compile time.
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
    when("""\n(\d+)\n""".r) {
      sendln { m: Match =>
        val previousAnswer = m.group(1)
        println(s"Got $previousAnswer")
        s"$previousAnswer + 3"
      }
    }
  }
  //This is a shortcut. It works just like the previous expect block.
  expect("""\n(\d+)\n""".r) {
    returning(_.group(1).toInt)
  }
}
e.run() //Returns 6 inside a Future[Int]
```

## License
Scala Expect is open source and available under the [MIT license](LICENSE).
