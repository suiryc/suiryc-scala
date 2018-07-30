package suiryc.scala.javafx.stage

import java.io.File
import javafx.stage.{DirectoryChooser, FileChooser}

/** DirectoryChooser/FileChooser helpers. */
object PathChoosers {

  /**
   * Finds valid folder.
   *
   * Choosers fail if given folder is not valid.
   * If valid, input parameter is returned.
   * Otherwise, the first valid folder (in parent hierarchy) is returned.
   *
   * @param folderOpt folder starting point if any
   * @return applicable valid folder if any
   */
  private def findValidPath(folderOpt: Option[File]): Option[File] = {
    @scala.annotation.tailrec
    def loop(folderOpt: Option[File]): Option[File] = {
      folderOpt match {
        case Some(folder) ⇒
          // DirectoryChooser/FileChooser will fail if we don't give it an actual folder
          if (folder.isDirectory) {
            Some(folder)
          } else {
            // Try parent folder
            loop(Option(folder.getParentFile))
          }

        case None ⇒
          // No valid folder
          None
      }
    }

    loop(folderOpt)
  }

  /**
   * Sets initial directory when possible.
   *
   * Checks given file points to an actual directory (otherwise DirectoryChooser
   * fails) and set it.
   * If not, set first actual directory in parent hierarchy if any.
   *
   * @param chooser directory chooser to setup
   * @param file file path to initially set
   */
  def setInitialPath(chooser: DirectoryChooser, file: File): Unit = {
    findValidPath(Option(file)).foreach(chooser.setInitialDirectory)
  }

  /**
   * Sets initial directory and filename when possible.
   *
   * Checks given file points to an actual directory (otherwise FileChooser
   * fails) and set it.
   * If not, set first actual directory in parent hierarchy if any.
   *
   * @param chooser file chooser to setup
   * @param file file path to initially set
   */
  def setInitialPath(chooser: FileChooser, file: File): Unit = {
    findValidPath(Option(file.getParentFile)).foreach(chooser.setInitialDirectory)
    chooser.setInitialFileName(file.getName)
  }

}
