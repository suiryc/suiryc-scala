package suiryc.scala.io

/** Writer expecting full lines. */
trait LineWriter {

  /**
   * Write one line.
   *
   * Note: line must not contain line ending (CR and/or LF).
   */
  def write(line: String)

}
