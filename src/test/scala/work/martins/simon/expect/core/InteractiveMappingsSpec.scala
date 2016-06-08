package work.martins.simon.expect.core

import org.scalatest.{Matchers, WordSpec}
import work.martins.simon.expect.TestUtils
import work.martins.simon.expect.core.actions._

class InteractiveMappingsSpec extends WordSpec with Matchers with TestUtils {
  def constructExpect(builderAction: => Unit): Expect[Int] = {
    new Expect("bc -i", defaultValue = 5)(
      ExpectBlock(
        StringWhen("For details type `warranty'.")(
          Sendln("1 + 2"),
          Returning {
            builderAction
            1
          }
        )
      ),
      ExpectBlock(
        RegexWhen("""\n(\d+)\n""".r)(
          SendlnWithRegex { m =>
            builderAction
            val previousAnswer = m.group(1)
            s"$previousAnswer + 3"
          }
        ),
        //Cheap way to test map, flatMap and transform on EndOfFileWhen
        EndOfFileWhen(
          Exit()
        )
      ),
      ExpectBlock(
        RegexWhen("""\n(\d+)\n""".r)(
          ReturningWithRegex{ m =>
            builderAction
            m.group(1).toInt
          },
          ReturningExpectWithRegex { m =>
            builderAction
            new Expect("ls", 2)()
          }
        ),
        //Cheap way to test map, flatMap and transform on TimeoutWhen
        TimeoutWhen(
          ReturningExpect {
            builderAction
            new Expect("ls", 3)()
          }
        )
      )
    )
  }

  def mapFunction(x: Int): Seq[Int] = Seq.fill(x)(x)

  def flatMap(x: Int): Expect[Seq[Int]] = new Expect("ls", mapFunction(x))()

  "An interactive Expect" when {
    "being mapped" should {
      "not cause the actions to be executed and must return the mapped result" in {
        val builder = new StringBuilder("")
        val addToBuilder = "some string"

        builder.result() shouldBe empty

        val e = constructExpect(builder.append(addToBuilder))

        val mappedExpect = e.map(mapFunction)

        //Ensure the actions were not executed in the map
        builder.result() shouldBe empty
        //Ensure the defaultValue was mapped
        mappedExpect.defaultValue shouldBe mapFunction(e.defaultValue)

        mappedExpect.whenReady { obtainedResult =>
          builder.result() shouldBe (addToBuilder * 4)
          obtainedResult shouldBe mapFunction(2) //The final returning will be the ReturningExpectWithRegex
        }
      }
    }

    "being flatMapped" should {
      "not cause the actions to be executed and must return the flatMapped result" in {
        val builder = new StringBuilder("")
        val addToBuilder = "some string"

        val e = constructExpect(builder.append(addToBuilder))

        val flatMappedExpect = e.flatMap(flatMap)

        //Ensure the actions were not executed in the map
        builder.result() shouldBe empty
        //Ensure the defaultValue was mapped
        flatMappedExpect.defaultValue shouldBe mapFunction(e.defaultValue)

        flatMappedExpect.whenReady { obtainedResult =>
          builder.result() shouldBe addToBuilder
          obtainedResult shouldBe mapFunction(1) //The final returning will be Returning
        }
      }
    }

    "being transformed: mapPF and flatMapPF are just defined for the default value" should {
      "not cause the actions to be executed and must return NoSuchElementException" in {
        val builder = new StringBuilder("")
        val addToBuilder = "some string"
        val newDefaultValue = -5

        val e = constructExpect(builder.append(addToBuilder))

        val transformedExpect: Expect[Int] = e.transform {
          PartialFunction.empty[Int, Expect[Int]]
        } {
          case e.defaultValue => newDefaultValue
        }

        //Ensure the actions were not executed in the transform
        builder.result() shouldBe empty
        //Ensure the defaultValue was mapped
        transformedExpect.defaultValue shouldBe newDefaultValue

        transformedExpect.whenReadyFailed { obtainedResult =>
          builder.result() shouldBe addToBuilder
          obtainedResult shouldBe a [NoSuchElementException]
        }
      }
    }

    "being transformed: mapping the default value, flatMapping the result" should {
      "not cause the actions to be executed and must return the transformed result" in {
        val builder = new StringBuilder("")
        val addToBuilder = "some string"
        val newDefaultvalue = Seq.empty[Int]

        val e = constructExpect(builder.append(addToBuilder))

        val transformedExpect: Expect[Seq[Int]] = e.transform {
          case 1 => flatMap(4)
        } {
          case e.defaultValue => newDefaultvalue
        }

        //Ensure the actions were not executed in the transform
        builder.result() shouldBe empty
        //Ensure the defaultValue was mapped
        transformedExpect.defaultValue shouldBe newDefaultvalue

        transformedExpect.whenReady { obtainedResult =>
          builder.result() shouldBe addToBuilder
          obtainedResult shouldBe mapFunction(4)
        }
      }
    }

    "being transformed: flatMapping the default value, mapping the result" should {
      "not cause the actions to be executed and must return the transformed result" in {
        val builder = new StringBuilder("")
        val addToBuilder = "some string"
        val newValue = 1 to 10

        val e = constructExpect(builder.append(addToBuilder))

        val transformedExpect = e.transform {
          case e.defaultValue => flatMap(6)
        } {
          case 1 | 6 | 2 => newValue
        }

        //Ensure the actions were not executed in the transform
        builder.result() shouldBe empty
        //Ensure the defaultValue was mapped
        transformedExpect.defaultValue shouldBe mapFunction(6)

        transformedExpect.whenReady { obtainedResult =>
          builder.result() shouldBe (addToBuilder * 4)
          obtainedResult shouldBe newValue
        }
      }
    }

    "being doubly transformed" should {
      "not cause the actions to be executed and must return the transformed result" in {
        val builder = new StringBuilder("")
        val addToBuilder = "some string"
        val newValue = 1 to 10

        val e = constructExpect(builder.append(addToBuilder))

        val transformedExpect = e.transform {
          case e.defaultValue => flatMap(6)
        } {
          case 1 => newValue
        }.transform {
          case `newValue` => flatMap(6)
        } {
          case Seq(6, 6, 6, 6, 6, 6) => newValue
        }

        //Ensure the actions were not executed in the map
        builder.result() shouldBe empty

        //Ensure the defaultValue was mapped
        transformedExpect.defaultValue shouldBe newValue

        transformedExpect.whenReady { obtainedResult =>
          builder.result() shouldBe addToBuilder
          obtainedResult shouldBe mapFunction(6)
        }
      }
    }
  }
}
