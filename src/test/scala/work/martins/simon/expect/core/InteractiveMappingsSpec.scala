package work.martins.simon.expect.core

import org.scalatest.{FlatSpec, Matchers}
import work.martins.simon.expect.TestUtils
import work.martins.simon.expect.core.actions._

class InteractiveMappingsSpec extends FlatSpec with Matchers with TestUtils {
  def constructExpect(builderAction: => Unit): Expect[Int] = {
    new Expect("bc -i", defaultValue = 5)(
      ExpectBlock(
        StringWhen("For details type `warranty'.")(
          Sendln("1 + 2")
        )
      ),
      ExpectBlock(
        RegexWhen("""\n(\d+)\n""".r)(
          SendlnWithRegex { m =>
            builderAction
            val previousAnswer = m.group(1)
            s"$previousAnswer + 3"
          }
        )
      ),
      ExpectBlock(
        RegexWhen("""\n(\d+)\n""".r)(
          ReturningWithRegex{ m =>
            builderAction
            m.group(1).toInt
          }
        )
      )
    )
  }

  def mapFunction(x: Int): Seq[Int] = Seq.fill(x)(x)

  def flatMap(x: Int): Expect[Seq[Int]] = new Expect("ls", mapFunction(x))()

  "Mapping an interactive Expect" should "not cause the actions to be executed and must return the mapped result" in {
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
        builder.result() shouldBe (addToBuilder * 2)
        obtainedResult shouldBe mapFunction(6)
    }
  }

  "FlatMapping an interactive Expect" should "not cause the actions to be executed and must return the flatMapped result" in {
    val builder = new StringBuilder("")
    val addToBuilder = "some string"

    val e = constructExpect(builder.append(addToBuilder))

    val flatMappedExpect = e.map(mapFunction)

    //Ensure the actions were not executed in the map
    builder.result() shouldBe empty
    //Ensure the defaultValue was mapped
    flatMappedExpect.defaultValue shouldBe mapFunction(e.defaultValue)

    flatMappedExpect.whenReady { obtainedResult =>
      builder.result() shouldBe (addToBuilder * 2)
      obtainedResult shouldBe mapFunction(6)
    }
  }

  "Transform an interactive Expect: mapping the default value, flatMapping the result" should "not cause the actions to be executed and must return the transformed result" in {
    val builder = new StringBuilder("")
    val addToBuilder = "some string"
    val newDefaultvalue = Seq.empty[Int]

    val e = constructExpect(builder.append(addToBuilder))

    val transformedExpect: Expect[Seq[Int]] = e.transform {
      case 6 => flatMap(4)
    } {
      case e.defaultValue => newDefaultvalue
    }

    //Ensure the actions were not executed in the map
    builder.result() shouldBe empty
    //Ensure the defaultValue was mapped
    transformedExpect.defaultValue shouldBe newDefaultvalue

    transformedExpect.whenReady { obtainedResult =>
      builder.result() shouldBe (addToBuilder * 2)
      obtainedResult shouldBe mapFunction(4)
    }
  }

  "Transform an interactive Expect: flatMapping the default value, mapping the result" should "not cause the actions to be executed and must return the transformed result" in {
    val builder = new StringBuilder("")
    val addToBuilder = "some string"
    val newValue: Seq[Int] = 1 to 10

    val e = constructExpect(builder.append(addToBuilder))

    val transformedExpect: Expect[Seq[Int]] = e.transform {
      case e.defaultValue => flatMap(6)
    } {
      case 6 => newValue
    }

    //Ensure the actions were not executed in the map
    builder.result() shouldBe empty
    //Ensure the defaultValue was mapped
    transformedExpect.defaultValue shouldBe mapFunction(6)

    transformedExpect.whenReady { obtainedResult =>
      builder.result() shouldBe (addToBuilder * 2)
      obtainedResult shouldBe newValue
    }
  }
}
