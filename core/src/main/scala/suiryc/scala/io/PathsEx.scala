package suiryc.scala.io

import java.io.File
import java.nio.file.{Files, Path, Paths}
import suiryc.scala.io.RichFile._
import suiryc.scala.sys.OS

/** Path (and filename) helpers. */
// scalastyle:off non.ascii.character.disallowed
object PathsEx {

  private val WINDOWS_PATH_ROOT_REGEXP = "^[a-zA-Z]:".r

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

  /** Rebuilds filename from 'atomic' name and extension. */
  def filename(atomicName: String, extension: String): String =
    if (extension.nonEmpty) s"$atomicName.$extension" else atomicName

  /** Gets 'atomic' name (filename without extension). */
  def atomicName(name: String): String = {
    val parts = filename(name).split('.')
    if (parts.length > 1) parts.view.slice(0, parts.length - 1).mkString(".")
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

  // Linux has no real restrictions on characters that can be used in
  // filenames. At most reserved characters can be used as long as they are
  // quoted or escaped.
  //
  // Windows have reserved characters that cannot be used:
  //   < (less than)
  //   > (greater than)
  //   : (colon)
  //   " (double quote)
  //   / (forward slash)
  //   \ (backslash)
  //   | (vertical bar or pipe)
  //   ? (question mark)
  //   * (asterisk)
  // See: https://msdn.microsoft.com/en-us/library/windows/desktop/aa365247%28v=vs.85%29.aspx#naming_conventions
  // It is then best to not use them (even on Linux, in case the file may be
  // copied/moved to Windows).
  //
  // A simple solution would be to replace them with _ (underscore):
  // str.replaceAll("""[<>:"/\\|?*]""", "_")
  //
  // Another solution is to replace them with Unicode alternatives.
  // See (search): https://unicode-search.net/unicode-namesearch.pl
  // Note: halfwidth/fullwidth variants may appear a little worse on Linux
  // (small blank space on the right of each letter/symbol)
  // Alternatives, listed in (subjective) preferred order:
  //
  // a<b a＜b
  // < (U+003C less than sign)
  // ＜ (U+FF1C fullwidth variant)
  //
  // a>b a＞b
  // > (U+003E greater than sign)
  // ＞ (U+FF1E fullwidth variant)
  //
  // a:b a꞉b a∶b a᎓b a：b
  // : (U+003A colon)
  // ꞉ (U+A789 modifier letter colon; appears a bit better than U+2236 in explorer)
  // ∶ (U+2236 ratio; does not appear better than U+A789 in explorer)
  // ᎓ (U+1393 ethopic tonal mark short rikrik; may not appear good on Linux)
  // ： (U+FF1A fullwidth variant)
  //
  // a"b a＂b
  // " (U+0022 quotation mark)
  // ＂ (U+FF02 fullwidth variant)
  //
  // a/b a⧸b a∕b a／b
  // / (U+002F solidus)
  // ⧸ (U+29F8 big solidus)
  // ∕ (U+2215 division mark; may appear better on Linux, but has almost no space between letters in Windows explorer)
  // ／ (U+FF0F fullwidth variant)
  //
  // a\b a⧹b a⧵b a＼b
  // \ (U+005C reverse solidus)
  // ⧹ (U+29F9 big reverse solidus)
  // ⧵ (U+29F5 reverse solidus operator)
  // ＼ (U+FF3C fullwidth variant)
  //
  // a|b aǀb a￨b a｜b
  // | (U+007C vertical line)
  // ǀ (U+01C0 latin letter dental click)
  // ￨ (U+FFE8 halfwidth variant)
  // ｜ (U+FF5C fullwidth variant)
  //
  // a?b a？b
  // ? (U+003F question mark)
  // ？ (U+FF1F fullwidth variant)
  //
  // a*b a∗b a⁎b a＊b
  // * (U+002A asterisk)
  // ∗ (U+2217 asterisk operator)
  // ⁎ (U+204E low asterisk)
  // ＊ (U+FF0A fullwidth variant)
  //
  // While the dot ('.') character is not reserved, it may be an issue when in
  // first or last position.
  // If needed, an alternative:
  // a.b a․b
  // . (U+002E full stop)
  // ․ (U+2024 one dot leader)
  private val sanitizedChars = Map[Char, Char](
    '<' -> '＜', '>' -> '＞',
    ':' -> '꞉',
    '"' -> '＂',
    '/' -> '⧸',
    '\\' -> '⧹',
    '|' -> 'ǀ',
    '?' -> '？',
    '*' -> '∗'
  )

  /** Sanitizes filename: replaces non-excluded reserved characters. */
  def sanitizeFilename(str: String, excluded: Set[Char]): String = {
    sanitizedChars.foldLeft(str) { (str, entry) =>
      if (excluded.contains(entry._1)) str
      else str.replace(entry._1, entry._2)
    }
  }

  /** Sanitizes filename: replaces reserved characters. */
  def sanitizeFilename(str: String): String = sanitizeFilename(str, Set.empty)

  /** Sanitizes path. */
  def sanitizePath(str: String): String = {
    // Path sanitization excludes the hierarchy separator.
    // On Windows, don't sanitize the path root.
    if (OS.isWindows && (str.length > 1) && WINDOWS_PATH_ROOT_REGEXP.pattern.matcher(str).find) {
      s"${str.substring(0, 2)}${sanitizeFilename(str.substring(2), Set(File.separatorChar))}"
    } else sanitizeFilename(str, Set(File.separatorChar))
  }

  /** Gets backup path (".bak" suffix) for a given file. */
  def backupPath(filepath: Path): Path = {
    filepath.resolveSibling(filepath.getFileName.toString + ".bak")
  }

  /**
   * Finds available path.
   *
   * Starting from given given path, find the first name that is available,
   * adding " (n)" suffix (before extension if any for files) with 'n' starting
   * from 1.
   *
   * @param path path to test
   * @param isFile whether this is supposed to be a file path
   * @return
   */
  def getAvailable(path: Path, isFile: Boolean = true): Path = {
    @scala.annotation.tailrec
    def loop(n: Int): Path = {
      val probe = if (n == 0) {
        path
      } else if (isFile) {
        val (base, ext) = path.toFile.baseAndExt
        val extOpt = Some(ext).filterNot(_.isEmpty)
        path.resolveSibling(s"$base ($n)${extOpt.map(v => s".$v").getOrElse("")}")
      } else {
        val name = path.name
        path.resolveSibling(s"$name ($n)")
      }
      if (Files.exists(probe)) loop(n + 1)
      else probe
    }

    loop(0)
  }

}
// scalastyle:on non.ascii.character.disallowed
