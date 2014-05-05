package suiryc.scala.io

import java.io.File
import scala.io.Source


object SourceEx {

  def autoCloseFile[T](file: File)(todo: Source => T): T = {
    val source = Source.fromFile(file)
    try {
      todo(source)
    }
    finally {
      source.close()
    }
  }

}
