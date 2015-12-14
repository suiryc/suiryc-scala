package suiryc.scala.io

import java.nio.file.{Path, Paths}


object PathsEx {

  def get(path: String): Path =
    if (path.startsWith("~")) {
      val rest = path.substring(2)
      val home = RichFile.userHome.toPath
      if (rest == "") home
      else home.resolve(rest)
    }
    else Paths.get(path)

  def filename(name: String): String =
    name.split("/").toList.reverse.head

  def atomicName(name: String): String =
    /* keep filename */
    filename(name).
      /* without extension */
      split("""\.""").reverse.tail.reverse.mkString(".")

  def atomicName(path: Path): String =
    atomicName(path.getFileName.toString)

  def extension(name: String): String =
    name.split("""\.""").toList.reverse.head

  def extension(path: Path): String =
    extension(path.getFileName.toString)

}
