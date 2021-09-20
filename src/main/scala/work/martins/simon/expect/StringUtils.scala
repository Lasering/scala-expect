package work.martins.simon.expect

import scala.quoted.*

object StringUtils:
  inline def escape(raw: String): String = raw
  /*inline def escape(inline raw: String): String = ${escapeImpl('{raw})}
  
  def escapeImpl(raw: Expr[String])(using Quotes): Expr[String] =
    import quotes.reflect.*
    Literal(StringConstant(raw.show)).asExprOf[String]*/
  
  def properCommand(command: Seq[String] | String): Seq[String] = command match
    case s: String => s.split("""\s+""").filter(_.nonEmpty).toSeq
    case seq: Seq[String] => seq
  
  implicit class IndentableString(s: String):
    def indent(level: Int = 1, text: String = "\t"): String = s.replaceAll("(?m)^", text * level)
    //def unindent(level: Int = 1, text: String = "\t"): String = s.replaceAll(s"(?m)^${text * level}", "")