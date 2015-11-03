package work.martins.simon.expect.core

trait AddBlock {
  /**
   * Execute arbitrary actions upon the instance where this is mixed.
   * @param block the block of code to execute.
   * @return the current instance (this) of the type where this trait was mixed (this.type).
   */
  def addBlock(block: this.type => Unit): this.type = {
    block(this)
    this
  }
}
