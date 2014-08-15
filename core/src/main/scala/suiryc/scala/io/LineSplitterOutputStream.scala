package suiryc.scala.io

import java.io.OutputStream
import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.{Charset, CoderResult}

/**
 * OutputStream that breaks data into lines.
 *
 * @param writer  where to output each line
 * @param charset charset to decode bytes
 */
class LineSplitterOutputStream(
  writer: LineWriter,
  charset: Charset = Charset.defaultCharset
) extends OutputStream
{

  /* Note: once flushed, the charset decoder *must not* be used again.
   * Thus:
   *  - only flush it upon closing the stream
   *  - stream 'flush' does nothing, and needs not be overridden
   *  - drop anything written after closing the stream
   */

  protected var closed = false
  protected val decoder = charset.newDecoder
  protected val charBuffer = CharBuffer.allocate(1024)
  protected var line = new StringBuilder()

  override def write(b: Int) {
    if (!closed)
      write(List[Byte](b.asInstanceOf[Byte]).toArray)
  }

  override def write(b: Array[Byte], off: Int, len: Int) {
    if (!closed)
      process(ByteBuffer.wrap(b, off, len), flush = false)
  }

  override def close() {
    if (!closed) {
      process(ByteBuffer.wrap(new Array[Byte](0)), flush = true)
      closed = true
    }
  }

  protected def process(bb: ByteBuffer, flush: Boolean) {
    @scala.annotation.tailrec
    def loop(f: => CoderResult) {
      val result = f

      if (charBuffer.position > 0)
        process(flush)
      if (result.isOverflow) loop(f)
    }

    loop(decoder.decode(bb, charBuffer, flush))
    if (flush)
      loop(decoder.flush(charBuffer))
  }

  protected def process(flush: Boolean) {

    val array = charBuffer.array

    def output() {
      if (line.endsWith("\r"))
        line.setLength(line.length - 1)
      writer.write(line.toString())
      line.clear()
    }

    @scala.annotation.tailrec
    def loop(offset: Int) {
      if (offset < charBuffer.position) {
        val nextOffset = {
          val v = array.indexOf('\n', offset)
          if ((v >= 0) || !flush) v
          else charBuffer.position
        }

        if (nextOffset >= 0) {
          line.appendAll(array, offset, nextOffset - offset)
          output()
          loop(nextOffset + 1)
        }
        else {
          line.appendAll(array, offset, charBuffer.position - offset)
        }
      }
    }

    loop(0)
    charBuffer.position(0)
  }

}
