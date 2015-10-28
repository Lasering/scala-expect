package codes.simon.expect.core

trait AddBlock {
  /**
   * Execute arbitrary actions upon this ${this.type}.
   * @param block the block of code that executes upon this ${this.type}.
   * @return this ${this.type}.
   */
  def addBlock(block: this.type => Unit): this.type = {
    block(this)
    this
  }
}
