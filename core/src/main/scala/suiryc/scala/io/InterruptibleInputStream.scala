package suiryc.scala.io

import java.io.{FilterInputStream, InputStream}
import scala.concurrent.duration._

/**
 * InputStream wrapper that can be interrupted.
 *
 * Some streams, e.g. stdin, are blocking and cannot be interrupted.
 * Depending on the kind of stream, some efficient solution could be used.
 *
 * In the case of stdin, System.in could be replaced by an instance that does
 * poll data from some kind of blocking (but interruptible) queue - e.g. a pair
 * of PipedInputStream/PipedOutputStream -, while a background thread would
 * fetch - when the intermediate buffer is not full - from stdin.
 *
 * In the case of file streams, NIO could be used.
 *
 * An easier solution, working with most streams, is to check whether data are
 * available before trying to read, or wait for some time before trying again,
 * breaking out of loop when the thread has been interrupted.
 * This only works if the underlying stream does expose available data when
 * appropriate.
 */
class InterruptibleInputStream(is: InputStream, loopDelay: FiniteDuration = 50.millis) extends FilterInputStream(is) {

  @inline
  private def interruptible(f: => Int): Int = {
    @scala.annotation.tailrec
    def loop(): Int = {
      if (Thread.interrupted()) -1
      else if (is.available() > 0) f
      else {
        Thread.sleep(loopDelay.toMillis)
        loop()
      }
    }
    loop()
  }

  override def read(): Int = {
    interruptible(super.read())
  }

  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    interruptible(super.read(b, off, len))
  }

}
