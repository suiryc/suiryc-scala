package suiryc.scala.io

import java.io.File
import scala.io.{Codec, Source}


object SourceEx {

  def autoCloseFile[T](file: File)(todo: Source => T)(implicit codec: Codec): T = {
    val source = Source.fromFile(file)
    try {
      todo(source)
    }
    finally {
      source.close()
    }
  }

  def autoCloseFile[T](file: File, enc: String)(todo: Source => T): T =
    autoCloseFile(file)(todo)(Codec(enc))

}
