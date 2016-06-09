package work.martins.simon.expect.core

import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import work.martins.simon.expect.TestUtils
import work.martins.simon.expect.core.actions._

import scala.util.Random

class MapFlatmapTransformSpec extends FlatSpec with Matchers with TestUtils with BeforeAndAfterEach {
  val addedValue = "this is it"
  val builders = Seq.fill(5)(new StringBuilder(""))
  val returnedResults = 1 to 5
  val defaultValue = returnedResults.sum //This ensures defaultValue and result are never the same

  private def constructExpect(when: When[Int]) = new Expect("ls", defaultValue)(ExpectBlock(when))

  val expects = Seq(
    constructExpect(StringWhen("README")(
      Returning {
        builders.head.append(addedValue)
        returnedResults.head
      }
    )), constructExpect(RegexWhen("LICENSE".r)(
      ReturningWithRegex { m =>
        builders(1).append(addedValue)
        returnedResults(1)
      }
    )), constructExpect(RegexWhen("build".r) (
      ReturningExpectWithRegex { m =>
        builders(2).append(addedValue)
        new Expect("ls", returnedResults(2))()
      }
    )), constructExpect(EndOfFileWhen(
      ReturningExpect {
        builders(3).append(addedValue)
        new Expect("ls", returnedResults(3))()
      }
    )),new Expect("bc -i", defaultValue)(
      ExpectBlock(
        RegexWhen("""bc (\d+)\.\d+""".r)(
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

  def mapFunction(x: Int): Seq[Int] = Seq.fill(x)(x)
  def flatMapFunction(x: Int): String = "NaN" * x + " Batman"
  def flatMap(x: Int): Expect[String] = {
    //To make it simple, it just returns the flatMapped defaultValue
    new Expect("ls", flatMapFunction(x))()
  }

  override protected def beforeEach(): Unit = builders.foreach(_.clear())

  def testActionsAndResult[R](expect: Expect[R], builder: StringBuilder, expectedResult: R): Unit = {
    //Ensure the actions were not executed in the mapping
    builder.result() shouldBe empty
    expect.whenReady { obtainedResult =>
      //Ensure the actions were executed
      builder.result() shouldBe addedValue
      obtainedResult shouldBe expectedResult
    }
  }
  def testActionsAndFailedResult[R](expect: Expect[R], builder: StringBuilder): Unit = {
    //Ensure the actions were not executed in the mapping
    builder.result() shouldBe empty
    expect.whenReadyFailed { obtainedResult =>
      //Ensure the actions were executed
      builder.result() shouldBe addedValue
      obtainedResult shouldBe a [NoSuchElementException]
    }
  }

  "Mapping an expect" should "should return the mapped result" in {
    for(((builder, result), expect) <- builders.zip(returnedResults).zip(expects)) {
      testActionsAndResult(expect.map(mapFunction), builder, mapFunction(result))
    }
  }

  "FlatMapping an expect" should "should return the flatMapped result" in {
    for(((builder, result), expect) <- builders.zip(returnedResults).zip(expects)) {
      testActionsAndResult(expect.flatMap(flatMap), builder, flatMapFunction(result))
    }
  }

  "Transforming an expect when the result is not in domain" should "should throw a NoSuchElementException" in {
    for((builder, expect) <- builders.zip(expects)) {
      val transformedExpect1 = expect.transform {
        case expect.defaultValue => flatMap(expect.defaultValue)
      } {
        PartialFunction.empty[Int, String]
      }

      testActionsAndFailedResult(transformedExpect1, builder)

      builder.clear()

      val transformedExpect2 = expect.transform {
        PartialFunction.empty[Int, Expect[String]]
      } {
        case expect.defaultValue => mapFunction(expect.defaultValue).mkString
      }

      testActionsAndFailedResult(transformedExpect2, builder)
    }
  }

  "Transforming an expect" should "return the transformed result" in {
    for (((builder, result), expect) <- builders.zip(returnedResults).zip(expects)) {
      def mapFunction2(s: String): Int = s.toCharArray.count(_ > 70)
      def flatMapFunction2(s: String): Array[Byte] = s.getBytes.filter(_ % 2 == 0)
      def flatMap2(s: String): Expect[Array[Byte]] = {
        //To make it simple, it just returns the flatMapped defaultValue
        new Expect("ls", flatMapFunction2(s))()
      }

      val newMappedResult = mapFunction(result).mkString
      val mappedExpect: Expect[String] = expect.transform {
        case expect.defaultValue => flatMap(expect.defaultValue)
      } {
        case `result` => newMappedResult
      }
      testActionsAndResult(mappedExpect, builder, newMappedResult)

      //Test map, flatMap and transform on a transformed expect
      builder.clear()
      testActionsAndResult(mappedExpect.map(mapFunction2), builder, mapFunction2(newMappedResult))
      builder.clear()
      testActionsAndResult(mappedExpect.flatMap(flatMap2), builder, flatMapFunction2(newMappedResult))
      builder.clear()
      testActionsAndResult(mappedExpect.transform{
        case mappedExpect.defaultValue => flatMap2(mappedExpect.defaultValue)
      }{
        case `newMappedResult` => Array.ofDim[Byte](5)
      }, builder, Array.ofDim[Byte](5))


      builder.clear()
      val newFlatMappedResult = flatMapFunction(result)
      val flatMappedExpect: Expect[String] = expect.transform {
        case `result` => flatMap(result)
      } {
        case expect.defaultValue => mapFunction(expect.defaultValue).mkString
      }
      testActionsAndResult(flatMappedExpect, builder, newFlatMappedResult)

      //Test map, flatMap and transform on a transformed expect
      builder.clear()
      testActionsAndResult(flatMappedExpect.map(mapFunction2), builder, mapFunction2(newFlatMappedResult))
      builder.clear()
      testActionsAndResult(flatMappedExpect.flatMap(flatMap2), builder, flatMapFunction2(newFlatMappedResult))
      builder.clear()
      testActionsAndResult(flatMappedExpect.transform{
        case `newFlatMappedResult` => new Expect("ls",  Array.ofDim[Byte](5))()
      }{
        case flatMappedExpect.defaultValue => flatMapFunction2(flatMappedExpect.defaultValue)
      }, builder, Array.ofDim[Byte](5))
    }
  }
}
