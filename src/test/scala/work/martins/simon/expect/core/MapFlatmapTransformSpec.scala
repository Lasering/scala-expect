package work.martins.simon.expect.core

import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AsyncWordSpec
import work.martins.simon.expect.{EndOfFile, TestUtils, Timeout}
import work.martins.simon.expect.core.actions.*
import scala.util.Random
import scala.util.matching.Regex.Match

class MapFlatmapTransformSpec extends AsyncWordSpec with BeforeAndAfterEach with TestUtils:
  val builders = Seq.fill(5)(new StringBuilder(""))
  val returnedResults = 1 to 5
  val defaultValue = returnedResults.sum
  
  val expects = Seq(
    constructExpect(defaultValue, When("README")(
      Returning {
        appendToBuilder(builders(0))
        returnedResults(0)
      }
    )),
    constructExpect(defaultValue, When("LICENSE".r)(
      Returning { (_: Match) =>
        appendToBuilder(builders(1))
        returnedResults(1)
      }
    )),
    constructExpect(defaultValue, When("build".r)(
      ReturningExpect { (_: Match) =>
        appendToBuilder(builders(2))
        new Expect("ls", returnedResults(2))()
      }
    )),
    constructExpect(defaultValue, When(EndOfFile)(
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
  
  override protected def beforeEach(): Unit = builders.foreach(_.clear())
  
  builders.zip(returnedResults).zip(expects).foreach { case ((builder, result), expect) =>
    s"The expect ${expect.hashCode()}" when {
      def f(x: Int): String = "NaN" * x + " Batman"
      def g(s: String): Double = s.length * Math.PI
      
      "mapped" should {
        "return the mapped result" in {
          testActionsAndResult(expect.map(f), builder, expectedResult = f(result))
        }
      }
      "flatMapped" should {
        "return the flatMapped result" in {
          val newExpect = expect.flatMap(r => constructExpect(defaultValue = f(r)))
          testActionsAndResult(newExpect, builder, expectedResult = f(result))
        }
      }
      "transformed" should {
        "throw a NoSuchElementException if the transform function if not defined for some result (in map)" in {
          val transformedExpect = expect.transform(
            // flapMapPF will work just for the defaultValue so transform will have to try mapPF
            { case t if t == expect.defaultValue => constructExpect(defaultValue = "a flatMapped value") },
            PartialFunction.empty
          )
          testActionsAndFailedResult(transformedExpect, builder)
        }
        "throw a NoSuchElementException if the transform function if not defined for some result (in flatMap)" in {
          val transformedExpect = expect.transform(
            PartialFunction.empty,
            // Since mapPF is only defined for the defaultValue it will fail for the other values
            { case t if t == expect.defaultValue => "a mapped value" }
          )
          testActionsAndFailedResult(transformedExpect, builder)
        }
        
        val mappedExpect = expect.transform(
          PartialFunction.empty,
          { case value => f(value) }
        )
        "return the correct result for: transform (map)" in {
          testActionsAndResult(mappedExpect, builder, expectedResult = f(result))
        }
        "return the correct result for: transform (map) followed by a map" in {
          testActionsAndResult(mappedExpect.map(g), builder, expectedResult = g(f(result)))
        }
        "return the correct result for: transform (map) followed by a flatMap" in {
          val newExpect = mappedExpect.flatMap(r => constructExpect(defaultValue = g(r)))
          testActionsAndResult(newExpect, builder, expectedResult = g(f(result)))
        }
        "return the correct result for: transform (map) followed by a transform" in {
          val transformedExpect = mappedExpect.transform(
            { case mappedExpect.defaultValue => constructExpect(defaultValue = 0) },
            { case _ => 25 }
          )
          testActionsAndResult(transformedExpect, builder, expectedResult = 25)
        }
        
        val flatMappedExpect = expect.transform(
          { case value => constructExpect(defaultValue = f(value)) },
          PartialFunction.empty
        )
        "return the correct result for: transform (flatMap)" in {
          testActionsAndResult(flatMappedExpect, builder, expectedResult = f(result))
        }
        "return the correct result for: transform (flatMap) followed by a map" in {
          testActionsAndResult(flatMappedExpect.map(g), builder, expectedResult = g(f(result)))
        }
        "return the correct result for: transform (flatMap) followed by a flatMap" in {
          val newExpect = flatMappedExpect.flatMap(r => constructExpect(defaultValue = g(r)))
          testActionsAndResult(newExpect, builder, expectedResult = g(f(result)))
        }
        "return the correct result for: transform (flatMap) followed by a transform" in {
          val transformedExpect = flatMappedExpect.transform(
            { case _ => constructExpect(defaultValue = 25) },
            PartialFunction.empty
          )
          testActionsAndResult(transformedExpect, builder, expectedResult = 25)
        }
      }
    }
  }