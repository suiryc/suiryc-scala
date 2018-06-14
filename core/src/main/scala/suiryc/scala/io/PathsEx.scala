package suiryc.scala.io

import java.nio.file.{Path, Paths}

/** Path (and filename) helpers. */
object PathsEx {

  /** Gets path (resolves leading '~' as user home). */
  def get(path: String): Path = {
    if (path.startsWith("~")) {
      val rest = path.substring(2)
      val home = RichFile.userHome.toPath
      if (rest == "") home
      else home.resolve(rest)
    }
    else Paths.get(path)
  }

  /** Gets filename (or basename; hierarchy leaf). */
  def filename(name: String): String =
    Paths.get(name).getFileName.toString

  /** Gets 'atomic' name (filename without extension). */
  def atomicName(name: String): String = {
    val parts = filename(name).split('.')
    if (parts.length > 1) parts.view(0, parts.length - 1).mkString(".")
    else parts.head
  }

  /** Gets 'atomic' name (filename without extension). */
  def atomicName(path: Path): String =
    atomicName(path.getFileName.toString)

  /** Gets extension (empty if none). */
  def extension(name: String): String = {
    val parts = filename(name).split('.')
    if (parts.length > 1) parts(parts.length - 1)
    else ""
  }

  /** Gets extension. */
  def extension(path: Path): String =
    extension(path.getFileName.toString)

}
