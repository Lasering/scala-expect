package work.martins.simon.expect

object StringUtils {
  //http://stackoverflow.com/questions/9913971/scala-how-can-i-get-an-escaped-representation-of-a-string
  def escape(raw: String): String = {
    import scala.reflect.runtime.universe._
    Literal(Constant(raw)).toString
  }

  def splitBySpaces(command: String): Seq[String] = command.split("""\s+""").filter(_.nonEmpty).toSeq

  implicit class IndentableString(s: String) {
    def indent(level: Int = 1, text: String = "\t"): String = s.replaceAll("(?m)^", text * level)
    def unindent(level: Int = 1, text: String = "\t"): String = s.replaceAll(s"(?m)^${text * level}", "")
  }
}
