package work.martins.simon.expect.core

import org.scalatest.{AsyncWordSpec, BeforeAndAfterEach}
import work.martins.simon.expect.TestUtils
import work.martins.simon.expect.core.actions._

import scala.util.Random

class MapFlatmapTransformSpec extends AsyncWordSpec with BeforeAndAfterEach with TestUtils {
  val builders = Seq.fill(5)(new StringBuilder(""))
  val returnedResults = 1 to 5
  val defaultValue = returnedResults.sum //This ensures defaultValue and result are never the same
  
  val expects = Seq(
    constructExpect(defaultValue, StringWhen("README")(
      Returning {
        builders(0).append(addedValue)
        returnedResults(0)
      }
    )), constructExpect(defaultValue, RegexWhen("LICENSE".r)(
      ReturningWithRegex { m =>
        builders(1).append(addedValue)
        returnedResults(1)
      }
    )), constructExpect(defaultValue, RegexWhen("build".r)(
      ReturningExpectWithRegex { m =>
        builders(2).append(addedValue)
        new Expect("ls", returnedResults(2))()
      }
    )), constructExpect(defaultValue, EndOfFileWhen(
      ReturningExpect {
        builders(3).append(addedValue)
        new Expect("ls", returnedResults(3))()
      }
    )), new Expect("bc -i", defaultValue)(
      ExpectBlock(
        RegexWhen( """bc (\d+)\.\d+""".r)(
          SendlnWithRegex { m =>
            builders(4).append(addedValue)
            s"${m.group(1)} + 3"
          }
        )
      ),
      ExpectBlock(
        TimeoutWhen(
          Returning(returnedResults(4)),
          //These two actions serve two purposes:
          // · Testing map, flatMap and transform for Send and Exit.
          // · Cause the RichProcess reader thread to be interrupted:
          //     We are writing a string bigger than the buffer size of the expect,
          //     then right away we close the program. The reader thread should still be reading the output
          //     when we close it, so it should be interrupted.
          Send {
            val array = Array.ofDim[Byte](2048)
            Random.nextBytes(array)
            array.map(_.toInt).mkString(" + ")
          },
          Exit()
        )
      )
    )
  )
  
  val tests: Seq[(StringBuilder, Int, Expect[Int])] = builders.zip(returnedResults).zip(expects).map {
    case ((builder, result), expect) ⇒ (builder, result, expect)
  }
  
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
  
  override protected def beforeEach(): Unit = builders.foreach(_.clear())
  
  tests.foreach { case (builder, result, expect) ⇒
    s"The expect ${expect.hashCode()}" when {
      "mapped" should {
        "return the mapped result" in {
          testActionsAndResult(expect.map(mapFunction), builder, mapFunction(result))
        }
      }
      "flatMapped" should {
        "return the flatMapped result" in {
          testActionsAndResult(expect.flatMap(flatMap), builder, flatMapFunction(result))
        }
      }
      "transformed" should {
        "throw a NoSuchElementException if the transform function if not defined for some result (in map)" in {
          val transformedExpect1 = expect.transform {
            case expect.defaultValue => flatMap(expect.defaultValue)
          } {
            PartialFunction.empty[Int, String]
          }
          testActionsAndFailedResult(transformedExpect1, builder)
        }
        "throw a NoSuchElementException if the transform function if not defined for some result (in flatMap)" in {
          val transformedExpect2 = expect.transform {
            PartialFunction.empty[Int, Expect[String]]
          } {
            case expect.defaultValue => mapFunction(expect.defaultValue).mkString
          }
          testActionsAndFailedResult(transformedExpect2, builder)
        }
  
        val newMappedResult = mapFunction(result).mkString
        val mappedExpect: Expect[String] = expect.transform {
          case expect.defaultValue => flatMap(expect.defaultValue)
        } {
          case `result` => newMappedResult
        }
        "return the correct result for: transform (map)" in {
          testActionsAndResult(mappedExpect, builder, newMappedResult)
        }
        "return the correct result for: transform (map) followed by a map" in {
          testActionsAndResult(mappedExpect.map(mapFunction2), builder, mapFunction2(newMappedResult))
        }
        "return the correct result for: transform (map) followed by a flatMap" in {
          testActionsAndResult(mappedExpect.flatMap(flatMap2), builder, flatMapFunction2(newMappedResult))
        }
        "return the correct result for: transform (map) followed by a transform" in {
          testActionsAndResult(mappedExpect.transform{
            case mappedExpect.defaultValue => flatMap2(mappedExpect.defaultValue)
          }{
            case `newMappedResult` => Array.ofDim[Byte](5)
          }, builder, Array.ofDim[Byte](5))
        }
          
        val newFlatMappedResult = flatMapFunction(result)
        val flatMappedExpect: Expect[String] = expect.transform {
          case `result` => flatMap(result)
        } {
          case expect.defaultValue => mapFunction(expect.defaultValue).mkString
        }
        "return the correct result for: transform (flatMap)" in {
          testActionsAndResult(flatMappedExpect, builder, newFlatMappedResult)
        }
        
        "return the correct result for: transform (flatMap) followed by a map" in {
          testActionsAndResult(flatMappedExpect.map(mapFunction2), builder, mapFunction2(newFlatMappedResult))
        }
        "return the correct result for: transform (flatMap) followed by a flatMap" in {
          testActionsAndResult(flatMappedExpect.flatMap(flatMap2), builder, flatMapFunction2(newFlatMappedResult))
        }
        "return the correct result for: transform (flatMap) followed by a transform" in {
          testActionsAndResult(flatMappedExpect.transform{
            case `newFlatMappedResult` => new Expect("ls",  Array.ofDim[Byte](5))()
          }{
            case flatMappedExpect.defaultValue => flatMapFunction2(flatMappedExpect.defaultValue)
          }, builder, Array.ofDim[Byte](5))
        }
      }
    }
  }
}
