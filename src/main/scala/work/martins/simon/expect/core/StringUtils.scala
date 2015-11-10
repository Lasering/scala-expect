package work.martins.simon.expect.core

object StringUtils {
  //http://stackoverflow.com/questions/9913971/scala-how-can-i-get-an-escaped-representation-of-a-string
  def escape(raw: String): String = {
    import scala.reflect.runtime.universe._
    Literal(Constant(raw)).toString
  }
}
