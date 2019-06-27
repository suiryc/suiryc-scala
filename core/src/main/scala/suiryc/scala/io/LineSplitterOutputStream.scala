package suiryc.scala.io

import java.io.OutputStream
import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.{Charset, CharsetDecoder, CoderResult}
import suiryc.scala.misc.Util

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

  import LineSplitterOutputStream._

  // Notes:
  // Once flushed, the charset decoder *must not* be used again. Thus:
  //  - only flush it upon closing the stream
  //  - stream 'flush' does nothing, and needs not be overridden
  //  - drop anything written after closing the stream
  //
  // We have two buffers:
  //  - 'charBuffer': fixed-length where decoded content is written to
  //  - 'line': line being built from decoded content (charBuffer)
  // When input is received, it is decoded ('charBuffer') and line endings are
  // searched for to build lines one at a time ('line' written once ending has
  // been reached). The whole input is processed, and trailing decoded content
  // (i.e. no line ending) is simply stored in 'line' ('charBuffer' emptied).
  // Upon closing, we then only need to flush the decoder and write the pending
  // 'line' if non empty.

  // Whether we are closed.
  protected var closed = false
  // Characters decoder.
  protected val decoder: CharsetDecoder = charset.newDecoder
  // Decoded content buffer.
  protected val charBuffer: CharBuffer = CharBuffer.allocate(defaultBufferSize)
  // Decoded line content (no line ending reached yet).
  protected val line = new StringBuilder()

  override def write(b: Int): Unit = {
    if (!closed) write(Array[Byte](b.toByte))
  }

  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    if (!closed) process(ByteBuffer.wrap(b, off, len), flush = false)
  }

  override def close(): Unit = {
    if (!closed) {
      process(ByteBuffer.wrap(new Array[Byte](0)), flush = true)
      closed = true
    }
  }

  protected def process(bb: ByteBuffer, flush: Boolean): Unit = {
    @scala.annotation.tailrec
    def loop(f: () => CoderResult): Unit = {
      val result = f()
      // Process decoded content if any.
      if (charBuffer.position() > 0) processBuffer()
      // If charBuffer was too small to decode the whole input ('overflow'),
      // keep on decoding (charBuffer content has been moved to 'line').
      if (result.isOverflow) loop(f)
    }

    // Decode input buffer.
    loop(() => decoder.decode(bb, charBuffer, flush))
    if (flush) {
      // Flush decoder and process decoded content if any.
      loop(() => decoder.flush(charBuffer))
      if (charBuffer.position() > 0) processBuffer()
      // Write pending chars if any.
      if (line.nonEmpty) writeLine()
    }
  }

  protected def writeLine(): Unit = {
    if (line.endsWith("\r")) line.setLength(line.length - 1)
    writer.write(line.toString())
    line.clear()
  }

  protected def processBuffer(): Unit = {
    val array = charBuffer.array
    val length = charBuffer.position()

    @scala.annotation.tailrec
    def loop(offset: Int): Unit = {
      if (offset < length) {
        // Search for next LF (up to actual written buffer position)
        val nextOffset = Util.indexOf(array, '\n', offset, length)
        if (nextOffset >= 0) {
          // LF found: append to line, write it and keep on processing.
          line.appendAll(array, offset, nextOffset - offset)
          writeLine()
          loop(nextOffset + 1)
        } else {
          // LF not found: append remaining decoded content to line.
          line.appendAll(array, offset, length - offset)
          ()
        }
      }
    }

    // Process decoded content to find lines.
    loop(0)
    // Empty decoded (and now processed) content.
    charBuffer.position(0)
    ()
  }

}

object LineSplitterOutputStream {
  val defaultBufferSize = 1024
}
