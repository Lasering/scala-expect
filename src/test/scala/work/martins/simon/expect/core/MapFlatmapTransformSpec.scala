package work.martins.simon.expect.core

import org.scalatest.{AsyncWordSpec, BeforeAndAfterEach}
import work.martins.simon.expect.{TestUtils, Timeout}
import work.martins.simon.expect.core.actions._

import scala.util.Random
import scala.util.matching.Regex.Match

class MapFlatmapTransformSpec extends AsyncWordSpec with BeforeAndAfterEach with TestUtils {
  val builders = Seq.fill(5)(new StringBuilder(""))
  val returnedResults = 1 to 5
  val defaultValue = returnedResults.sum
  
  val expects = Seq(
    constructExpect(defaultValue, StringWhen("README")(
      Returning {
        appendToBuilder(builders(0))
        returnedResults(0)
      }
    )),
    constructExpect(defaultValue, RegexWhen("LICENSE".r)(
      Returning { _: Match =>
        appendToBuilder(builders(1))
        returnedResults(1)
      }
    )),
    constructExpect(defaultValue, RegexWhen("build".r)(
      ReturningExpect { _: Match =>
        appendToBuilder(builders(2))
        new Expect("ls", returnedResults(2))()
      }
    )),
    constructExpect(defaultValue, EndOfFileWhen()(
      ReturningExpect {
        appendToBuilder(builders(3))
        new Expect("ls", returnedResults(3))()
      }
    )),
    new Expect("bc -i", defaultValue)(
      ExpectBlock(
        When( """bc (\d+)\.\d+""".r)(
          Sendln { m =>
            appendToBuilder(builders(4))
            s"${m.group(1)} + 3"
          }
        )
      ),
      ExpectBlock(
        When(Timeout)(
          Returning(returnedResults(4)),
          //These two actions serve two purposes:
          // · Testing map, flatMap and transform for Send and Exit.
          // · Sending a very big string hopefully bigger than the RichProcess' buffer is capable of handling.
          Send {
            val array = Array.ofDim[Byte](1024 * 1024)
            Random.nextBytes(array)
            array.map(_.toInt).mkString(" + ")
          },
          Exit()
        )
      )
    )
  )

  def baseExpect[T](defaultValue: T): Expect[T] = new Expect("ls", defaultValue)()
  def baseExpectWithFunction[I, O](f: I => O)(t: I): Expect[O] = new Expect("ls", f(t))()

  override protected def beforeEach(): Unit = builders.foreach(_.clear())

  builders.zip(returnedResults).zip(expects).foreach { case ((builder, result), expect) =>
    s"The expect ${expect.hashCode()}" when {
      def toTuple2(x: Int) = (x, x)
      "mapped" should {
        "return the mapped result" in {
          testActionsAndResult(expect.map(toTuple2), builder, toTuple2(result))
        }
      }
      "flatMapped" should {
        "return the flatMapped result" in {
          testActionsAndResult(expect.flatMap(baseExpectWithFunction(toTuple2)), builder, toTuple2(result))
        }
      }
      "transformed" should {
        "throw a NoSuchElementException if the transform function if not defined for some result (in map)" in {
          val transformedExpect = expect.transform(
            // flapMapPF will fail (except for the defaultValue) so transform will have to try mapPF
            { case t if t == expect.defaultValue =>  baseExpect(t)},
            // mapPF will fail too (what we are testing)
            PartialFunction.empty
          )
          testActionsAndFailedResult(transformedExpect, builder)
        }
        "throw a NoSuchElementException if the transform function if not defined for some result (in flatMap)" in {
          val transformedExpect = expect.transform(
            // flapMapPF will fail
            PartialFunction.empty,
            // Since mapPF is only defined for the defaultValue it will fail for the other values
            { case t if t == expect.defaultValue => t }
          )
          testActionsAndFailedResult(transformedExpect, builder)
        }

        // TODO: remove/change these functions
        def mapFunction(x: Int): Seq[Int] = Seq.fill(x)(x)
        def flatMapFunction(x: Int): String = "NaN" * x + " Batman"
        def flatMap(x: Int): Expect[String] = {
          //To make it simple, it just returns the flatMapped defaultValue
          new Expect("ls", flatMapFunction(x))()
        }

        def mapFunction2(s: String): Int = s.toCharArray.count(_ > 70)
        def flatMapFunction2(s: String): Array[Byte] = s.getBytes.filter(_ % 2 == 0)
        def flatMap2(s: String): Expect[Array[Byte]] = {
          //To make it simple, it just returns the flatMapped defaultValue
          new Expect("ls", flatMapFunction2(s))()
        }

        val mappedExpect = expect.transform(
          PartialFunction.empty,
          { case t => mapFunction(t).mkString }
        )
        "return the correct result for: transform (map)" in {
          testActionsAndResult(mappedExpect, builder, mapFunction(result).mkString)
        }
        "return the correct result for: transform (map) followed by a map" in {
          testActionsAndResult(mappedExpect.map(mapFunction2), builder, mapFunction2(mapFunction(result).mkString))
        }
        "return the correct result for: transform (map) followed by a flatMap" in {
          testActionsAndResult(mappedExpect.flatMap(flatMap2), builder, flatMapFunction2(mapFunction(result).mkString))
        }
        "return the correct result for: transform (map) followed by a transform" in {
          val transformedExpect = mappedExpect.transform(
            { case mappedExpect.defaultValue => flatMap2(mappedExpect.defaultValue) },
            { case _ => Array.ofDim[Byte](5) }
          )
          testActionsAndResult(transformedExpect, builder, Array.ofDim[Byte](5))
        }

        val flatMappedExpect: Expect[String] = expect.transform(
          { case t => flatMap(t) },
          PartialFunction.empty
        )
        "return the correct result for: transform (flatMap)" in {
          testActionsAndResult(flatMappedExpect, builder, flatMapFunction(result))
        }
        "return the correct result for: transform (flatMap) followed by a map" in {
          testActionsAndResult(flatMappedExpect.map(mapFunction2), builder, mapFunction2(flatMapFunction(result)))
        }
        "return the correct result for: transform (flatMap) followed by a flatMap" in {
          testActionsAndResult(flatMappedExpect.flatMap(flatMap2), builder, flatMapFunction2(flatMapFunction(result)))
        }
        "return the correct result for: transform (flatMap) followed by a transform" in {
          val transformedExpect = flatMappedExpect.transform(
            { case _ => new Expect("ls",  Array.ofDim[Byte](5))() },
            PartialFunction.empty
          )
          testActionsAndResult(transformedExpect, builder, Array.ofDim[Byte](5))
        }
      }
    }
  }
}
