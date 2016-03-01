package suiryc.scala.javafx.stage

import java.io.File
import javafx.stage.FileChooser

/** FileChooser helpers. */
object FileChoosers {

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
    @scala.annotation.tailrec
    def setInitial(folderOpt: Option[File], filenameOpt: Option[String]): Unit = {
      folderOpt match {
        case Some(folder) =>
          // FileChooser will fail if we don't give it an actual folder
          if (folder.isDirectory) {
            chooser.setInitialDirectory(folder)
            filenameOpt.foreach(chooser.setInitialFileName)
          } else {
            // Try parent folder (without initial filename)
            setInitial(Option(folder.getParentFile), None)
          }

        case None =>
        // Cannot set initial folder/file since we don't have a valid value
      }
    }

    setInitial(Option(file.getParentFile), Option(file.getName))
  }

}
