package suiryc.scala.sbt

import sbt._
import sbtassembly._

object AssemblyEx {

  // Notes on assembly plugin:
  // A temporary folder is created to gather the content of the generated
  // assembly jar.
  // Assembly has many sources: either jars content, or folders (e.g. project
  // 'resources').
  // For each source, a unique temporary folder is created, which will contain
  // the content of each source. The plugin will also create special files
  // indicating the details of each source; the details can be retrieved with
  // the AssemblyUtils.sourceOfFileForMerge function.
  // The plugin will gather the Seq of all files with the same path (relatively
  // to its source), and use assemblyMergeStrategy to determine what to do: it
  // returns a MergeStrategy handling all the gathered files. The default is
  // to deduplicate, which only works if there is either only one file, or if
  // all duplicates have the same content.
  // Applying strategies on all sources returns list of pairs (local path and
  // relative path in jar) which, cumulated, are used to build the jar content.
  //
  // The MergeStrategy function has the following parameters:
  //  - tempDir: the temporary folder where assembly jar content is gathered
  //  - path: the file path (relative to the base)
  //    - same value than the matching key in assemblyMergeStrategy
  //  - files: Seq of source files sharing the same (relative) path
  //    - these are the paths in the temporary folders created
  // sourceOfFileForMerge returns the following information:
  //  - the path of the original jar or directory the file belongs to
  //  - the given file path base
  //  - the given file (relative) path: same as 'path' in MergeStrategy
  //  - whether the source is a jar, or folder
  //
  // Example with a source folder:
  //  - MergeStrategy
  //    tempDir=/path/to/project/target/streams/_global/assembly/_global/streams/assembly
  //    path=application.conf
  //    file=<tempDir>/aba3269c8afbaa3d63a5fd1544401e198c7935b2_dir/application.conf
  //  - sourceOfFileForMerge
  //    /path/to/project/target/scala-2.13/classes
  //    <tempDir>/aba...5b2_dir
  //    application.conf
  //    false
  // Example with a source jar:
  //  - MergeStrategy
  //    tempDir=/path/to/project/target/streams/_global/assembly/_global/streams/assembly
  //    path=reference.conf
  //    file=<tempDir>/d0f...709/reference.conf
  //  - sourceOfFileForMerge
  //    $HOME/.cache/coursier/.../<org/name>/<jar-name>_<scala-version>/<jar-version>/<jar-name>_<scala-version>-<jar-version>.jar
  //    <tempDir>/d0f...709
  //    reference.conf
  //    true

  // Dummy strategy used to print details.
  // scalastyle:off token
  val trace: MergeStrategy = new MergeStrategy {
    val name = "trace"
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
      val strat = MergeStrategy.defaultMergeStrategy(path)
      println(s"tempDir=$tempDir path=$path defaultStrategy=${strat.name}")
      files.foreach { f =>
        sourceOfFileForMerge(tempDir, f) match {
          case (owner, base, p, inJar) =>
            println(s"  file=$f\n    owner=$owner\n    inJar=$inJar\n    base=$base\n    p=$p")
        }
      }
      strat.apply(tempDir, path, files)
    }
  }
  // scalastyle:on token

  // Strategy used to concat files in order depending on source: jars then dirs.
  // This is useful when libraries use application.conf, either to override
  // other libraries settings, or mistakenly instead of reference.conf.
  // The actual application needs to have its application.conf read first, which
  // is 'easy' when listing jar dependencies in classpath (order them by
  // importance), but more complicated or unpredictable when using default
  // assembly behaviour.
  //
  // Alternatively a strategy could concatenate jar application.conf into
  // reference.conf, but:
  //  - it would need to be applied for both application.conf and reference.conf
  //  - it would be a bit more complicated by needing
  //    - re-implement the 'concat' function: ignore empty files list, append to
  //      target, return pair only if target does not exist (better not list the
  //      same jar entry twice)
  //    - (private) sha1string function for deterministic temporary filenames
  //  - would break things if the library used its application.conf to override
  //    another library settings; or we would have to concat differently whether
  //    the target file exists (prepend jar reference.conf if it already exists)
  val concatJarThenDir: MergeStrategy = new MergeStrategy {
    val name = "concatJarThenDir"
    def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
      // If there is only one file, just use it.
      if (files.size == 1) Right(Seq(files.head -> path))
      else {
        // Otherwise concat jar files then dir files.
        val (inJars, inDirs) = files.partition(sourceOfFileForMerge(tempDir, _)._4)
        MergeStrategy.concat.apply(tempDir, path, inJars ++ inDirs)
      }
    }
  }

  // sbt-assembly has a AssemblyUtils.sourceOfFileForMerge function, but v1.0.0
  // made the object private, mistakenly preventing this function usage.
  // Until it is fixed, copy the necessary source code.
  // Se: https://github.com/sbt/sbt-assembly/issues/435
  private val PathRE = "([^/]+)/(.*)".r

  private def sourceOfFileForMerge(tempDir: File, f: File): (File, File, String, Boolean) = {
    val baseURI = tempDir.getCanonicalFile.toURI
    val otherURI = f.getCanonicalFile.toURI
    val relative = baseURI.relativize(otherURI)
    val PathRE(head, tail) = relative.getPath
    val base = tempDir / head

    if ((tempDir / (head + ".jarName")).exists()) {
      val jarName = IO.read(tempDir / (head + ".jarName"), IO.utf8)
      (new File(jarName), base, tail, true)
    } else {
      val dirName = IO.read(tempDir / (head + ".dir"), IO.utf8)
      (new File(dirName), base, tail, false)
    } // if-else
  }

}
