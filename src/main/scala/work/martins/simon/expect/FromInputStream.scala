package work.martins.simon.expect

sealed trait FromInputStream
case object StdOut extends FromInputStream
case object StdErr extends FromInputStream
