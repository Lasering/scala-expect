package work.martins.simon.expect

import scala.annotation.targetName

enum FromInputStream derives CanEqual:
  case StdOut, StdErr

object EndOfFile
object Timeout

@targetName("PartialFunction")
infix type /=>[-A, +B] = PartialFunction[A, B]