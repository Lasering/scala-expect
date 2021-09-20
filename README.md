# Scala Expect [![license](http://img.shields.io/:license-MIT-blue.svg)](LICENSE)
[![Scaladoc](https://javadoc.io/badge2/work.martins.simon/scala-expect_3.1/javadoc.svg)](https://lasering.github.io/scala-expect/latest/api/work/martins/simon/expect/index.html)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/work.martins.simon/scala-expect_3.1/badge.svg?maxAge=604800)](https://maven-badges.herokuapp.com/maven-central/work.martins.simon/scala-expect_3.1)

[![example workflow](https://github.com/Lasering/scala-expect/actions/workflows/ci.yml/badge.svg)](https://github.com/Lasering/scala-expect/actions)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/156e74a155e64789a241ebb25c227598)](https://www.codacy.com/app/IST-DSI/scala-expect)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/156e74a155e64789a241ebb25c227598)](https://www.codacy.com/gh/Lasering/scala-expect/dashboard)
[![BCH compliance](https://bettercodehub.com/edge/badge/Lasering/scala-expect)](https://bettercodehub.com/results/Lasering/scala-expect)

A Scala implementation of a very small subset of the widely known TCL expect.

Scala Expect comes with three different flavors: `core`, `fluent` and `dsl`.

## Install
```scala
libraryDependencies += "work.martins.simon" %% "scala-expect" % "6.0.0"
```

## Core
#### Advantages
* Closer to metal / basis for the other flavors.
* Immutable and therefore thread-safe.
* Most errors will be caught at compile time (eg. you won't be able to use a `Send` with regex inside a `When` matching strings).

#### Disadvantages
* Pesky commas and parenthesis everywhere.
* Can't cleanly add expect blocks/whens/actions based on a condition.

#### Example
```scala
import work.martins.simon.core._
import scala.concurrent.ExecutionContext.Implicits.global

val e = new Expect("bc -i", defaultValue = 5)(
  ExpectBlock(
    When("For details type `warranty'.")(
      Sendln("1 + 2"),
    ),
  ),
  ExpectBlock(
    When("""\n(\d+)\n""".r)(
      Sendln { m =>
        val previousAnswer = m.group(1)
        println(s"Got $previousAnswer")
        s"$previousAnswer + 3"
      },
    ),
  ),
  ExpectBlock(
    When("""\n(\d+)\n""".r)(
      Returning(_.group(1).toInt),
    ),
  ),
)
e.run() //Returns 6 inside a Future[Int]
```

## Fluent
#### Advantages
* Fewer commas and parenthesis.
* Most errors will be caught at compile time.
* Easy to add expect blocks/whens/actions based on a condition.
* Easy to refactor the creation of expects.
* Can be called from Java easily.

#### Disadvantages
* Some overhead since the fluent expect is just a builder for a core expect.
* Mutable - the fluent expect has to maintain a state of the objects that have been built.
* Reformatting code in IDE's will mess the custom indentation.

#### Example
```scala
import work.martins.simon.fluent._
import scala.concurrent.ExecutionContext.Implicits.global

val e = new Expect("bc -i", defaultValue = 5) {
  expect
    .when("For details type `warranty'.")
      .sendln("1 + 2")
  expect
    .when("""\n(\d+)\n""".r)
      .sendln { m =>
        val previousAnswer = m.group(1)
        println(s"Got $previousAnswer")
        s"$previousAnswer + 3"
      }
  expect
    .when("""\n(\d+)\n""".r)
      .returning(_.group(1).toInt)
}
e.run() //Returns 6 inside a Future[Int]
```

## DSL
#### Advantages
* Code will be indented in blocks so IDE's won't mess the indentation.
* Syntax more close to the TCL expect.
* Easy to add expect blocks/whens/actions based on a condition.
* Easy to refactor the creation of expects.

#### Disadvantages
* More overhead than the fluent expect since it's just a wrapper around fluent expect.
* Mutable - it uses a fluent expect as the backing expect and a mutable stack to keep track of the current context.

#### Example
```scala
import work.martins.simon.dsl._
import scala.concurrent.ExecutionContext.Implicits.global

val e = new Expect("bc -i", defaultValue = 5) {
  expect {
    when("For details type `warranty'.") {
      sendln("1 + 2")
    }
  }
  expect {
    when("""\n(\d+)\n""".r) {
      sendln { m =>
        val previousAnswer = m.group(1)
        println(s"Got $previousAnswer")
        s"$previousAnswer + 3"
      }
    }
  }
  expect {
    when("""\n(\d+)\n""".r) {
      returning(_.group(1).toInt)
    }
  }
}
e.run() //Returns 6 inside a Future[Int]
```

## License
Scala Expect is open source and available under the [MIT license](LICENSE).