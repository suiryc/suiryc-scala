package suiryc.scala.io

import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import scala.io.{Codec, Source}


object SourceEx {

  def autoCloseFile[T](file: File)(todo: Source => T)(implicit codec: Codec): T = {
    val source = Source.fromFile(file)
    try {
      todo(source)
    } finally {
      source.close()
    }
  }

  def autoCloseFile[T](file: File, codec: Codec)(todo: Source => T): T =
    autoCloseFile(file)(todo)(codec)

  def autoCloseFile[T](file: File, enc: String)(todo: Source => T): T =
    autoCloseFile(file)(todo)(Codec(enc))

  @inline
  private def getLinesIterator(path: Path)(implicit codec: Codec): Iterator[String] = {
    new Iterator[String] {
      private val source = if (path.getFileName.toString.endsWith(".gz")) {
        Source.fromInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(path.toFile))))
      } else {
        Source.fromFile(path.toFile)
      }
      private val it = source.getLines()
      override def hasNext: Boolean = {
        val r = it.hasNext
        if (!r) source.close()
        r
      }
      override def next(): String = it.next()
    }
  }

  /**
   * Gets lines iterator for given file path.
   *
   * Automatically handles (on-the-fly) uncompressing gzip files.
   * Automatically closes source when EOF is reached.
   * Note: usual iterator reading does call 'hasNext' on which we rely to
   * close source when done.
   */
  def getLines(path: Path)(implicit codec: Codec): Iterator[String] = {
    getLinesIterator(path)(codec)
  }

  def getLines(path: Seq[Path])(implicit codec: Codec): Iterator[String] = {
    path.foldLeft(Iterator.empty[String])(_ ++ getLines(_))
  }

}
