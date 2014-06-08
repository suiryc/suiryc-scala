package suiryc.scala.io

import java.io.{InputStream, OutputStream}


object IOStream {

  def transfer[T <: OutputStream](
    input: InputStream,
    output: T,
    cb: (Array[Byte], Int, Int) => Unit = (_, _, _) => {}
  ): (T, Long) =
  {
    /* XXX - parameter (with default value, or from Config) to set buffer size ? */
    val buffer = new Array[Byte](16 * 1024)
    /* Note: *DO NOT* use Stream.foldLeft as it materializes the next element
     * before calling the folding function (scala 2.10).
     * To determine the total size transferred, possible solutions are then:
     *  - Stream.map.sum (which does foldLeft, but on our element that did read+write)
     *  - an alternative version of foldLeft, e.g.
//    @scala.annotation.tailrec
//    def foldLeft[A,B](stream: Stream[A])(z: B)(op: (B, A) => B): B = {
//      if (stream.isEmpty) z
//      else {
//        val r = op(z, stream.head)
//        foldLeft(stream.tail)(r)(op)
//      }
//    }
     */
    val size = Stream.continually(input.read(buffer)).takeWhile(_ != -1).map { count =>
      output.write(buffer, 0 , count)
      cb(buffer, 0, count)
      count.longValue
    }.sum
    (output, size)
  }

}
