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

  def filename(name: String) =
    name.split("/").toList.reverse.head

  def atomicName(name: String) =
    /* keep filename */
    filename(name).
      /* without extension */
      split("""\.""").reverse.tail.reverse.mkString(".")

  def extension(name: String) =
    name.split("""\.""").toList.reverse.head

}
