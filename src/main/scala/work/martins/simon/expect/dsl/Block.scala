package work.martins.simon.expect.dsl

trait Block[R] {
  def apply(block: => DSL[R]) : DSL[R] with Block[R]
}
