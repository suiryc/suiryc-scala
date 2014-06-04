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

}
