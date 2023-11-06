package suiryc.scala.sbt

import java.util.Date
import scala.sys.process.{Process, ProcessBuilder, ProcessLogger}
import scala.util.Try

object Versioning {

  // Notes:
  // 'sbt-git' plugin gets all tags, filter them and decide the appropriate one
  // (by parsing and comparing version numbers). It easily handles mixed tag
  // versions with or without the 'v' prefix, but fails if matching tags have
  // characters other than digits and '.'.
  // 'sbt-dynver' plugin, and 'dynver' library, rely on 'git describe' to get
  // the appropriate tag. It does not fail due to tag format, but does not
  // easily handle versatile tag formats (e.g. mixed with or without 'v').
  //
  // Instead, do a bit of both: rely on 'git tag' to get tags, filter them and
  // compare them to decide the most appropriate one, and refine information
  // with 'git describe'. Also have fallbacks when no tag matches or even if
  // there are no git information at all.
  //
  // Tags taken into account must have the format 'A.B.C' where A/B/C are
  // numbers, with optional leading 'v', and with optional suffix (no format
  // restriction).
  // Versioning examples (without pending modifications in tracked files):
  //  - 'A.B.C': tag exact match
  //  - 'A.B.C+N-xxxxxxxx': commit 'xxxxxxxx', N after tag version 'A.B.C'
  //  - 'xxxxxxxx': commit 'xxxxxxxx', without matching tag
  // '+YYYYmmdd_HHMM' timestamp suffix, or '-SNAPSHOT', is appended when there
  // are pending (not yet committed) modifications in tracked files.
  // 'YYYYmmdd_HHMM', or 'SNAPSHOT, format is used as fallback if we cannot get
  // the expected information from git.

  private case class TagVersion(tag: String, versioning: List[Int]) extends Ordered[TagVersion] {
    override def compare(other: TagVersion): Int = {
      // If we ever get different versioning sizes (e.g. '1.2' and '1.2.3'),
      // assume the missing numbers are 0 (e.g. '1.2' would be compared to
      // '1.2.3' as '1.2.0').
      // We only need to compare the first level which has different values.
      versioning
        .zipAll(other.versioning, 0, 0)
        .dropWhile {
          case (a, b) => a == b
        }
        .headOption
        .map {
          case (a, b) => a - b
        }
        .getOrElse(0)
    }
  }

  private object TagVersion {
    def build(tag: String): Option[TagVersion] = {
      val trimmed = tag.trim
      // Only keep tags of the form 'A.B' or 'vA.B', optionally followed by
      // another part ('.???', '-???', '_???').
      if (trimmed.matches("""v?[0-9]+\.[0-9]+(?:[-\._].*|$)""")) {
        val version = trimmed.split("""\D+""").toList.map(_.trim).filterNot(_.isEmpty).map(_.toInt)
        Some(TagVersion(trimmed, version))
      } else {
        None
      }
    }
  }

  /** Minimal size for commit ids. */
  private val ABBREV_LENGTH = 8

  /** Formats timestamp: YYYYmmdd_HHMM */
  private def timestamp(d: Date): String = f"$d%tY$d%tm$d%td_$d%tH$d%tM"

  private def modifiedSuffix(sep: Boolean, ts: Boolean, d: Date): String = {
    s"${if (sep) if (ts) "+" else "-"}${if (ts) timestamp(d) else "SNAPSHOT"}"
  }

  /** Silently tries to run process and get stdout. */
  private def silent(builder: ProcessBuilder): Try[String] = {
    Try(builder.!!(ProcessLogger(_ => ())))
  }

  /** Chooses the most appropriate tag for versioning. */
  private def chooseTagVersion: Option[TagVersion] = {
    // Get tags reachable from the current commit: these are part of our commit
    // history.
    // Only keep applicable tags, and sort them to get the highest number,
    // supposedly associated to the latest tag version.
    silent(Process(List("git", "tag", "--merged")))
      .map { stdout =>
        stdout.linesIterator
          .map(TagVersion.build)
          .filter(_.isDefined)
          .flatten
          .toList
          .sorted
          .lastOption
      }
      .toOption
      .flatten
  }

  /**
   * Gets git description.
   *
   * Examples when matching tag (without modifications on tracked files):
   *  "vA.B.C": on tag "vA.B.C".
   *  "vA.B.C+N-xxxxxxxx": on commit "xxxxxxxx", N commits after tag "vA.B.C".
   * Example when not matching tag (without modifications):
   *  "xxxxxxxx": on commit "xxxxxxxx"
   * When there are modifications on tracked files, "+YYYYmmdd_HHMM" timestamp
   * suffix is added, or "-SNAPSHOT" if requested.
   */
  private def gitDescription(v: Option[TagVersion], ts: Boolean, date: Date): Option[String] = {
    // We either want to build description relatively to a specific tag, or an
    // absolute (commit id) one.
    val args = v match {
      case Some(v) => List("--tags", "--match", v.tag)
      case None => Nil
    }
    // Add requested suffix if repository is dirty (at least one tracked file
    // is modified and not yet committed).
    silent(Process(List("git", "describe", "--always", s"--abbrev=$ABBREV_LENGTH", s"--dirty=${modifiedSuffix(sep = true, ts, date)}") ::: args))
      .map { stdout =>
        stdout.linesIterator
          .map(_.trim)
          .filterNot(_.isEmpty)
          .toList
          .headOption
          .map { s =>
            // Drop optional leading 'v', use '+' character do denote distance
            // (number of tags) from tag, and drop the 'g' before latest commit
            // id.
            s
              .replaceFirst("^v", "")
              .replaceFirst("""^(.*?)-([0-9]+)-g([0-9a-f]{8,}(?:\+|$))""", "$1+$2-$3")
          }
      }
      .toOption
      .flatten
  }

  /** Gets total number of commits in our history. */
  private def commitsCount: Option[Int] = {
    silent(Process(List("git", "rev-list", "--count", "HEAD")))
      .map(_.trim.toInt)
      .toOption
  }

  /** Gets latest commit id. */
  def commitId: Option[String] = {
    silent(Process(List("git", "rev-parse", "HEAD")))
      .map(_.trim)
      .toOption
  }

  /** Determines version, with timestamp suffix when applicable */
  def version: String = version(ts = true)

  /**
   * Determines version.
   *
   * @param ts whether to use timestamp or "SNAPSHOT" suffix when applicable.
   */
  def version(ts: Boolean): String = {
    // First try to determine the latest version tag, and build description
    // relatively to it.
    // If we cannot, try to build description from number of commits.
    // Otherwise, use a timestamp.
    val date = new Date()
    val descOpt: Option[String] = for {
      v <- chooseTagVersion
      desc <- gitDescription(Some(v), ts, date)
    } yield desc
    descOpt.orElse {
      for {
        distance <- commitsCount
        desc <- gitDescription(None, ts, date)
      } yield s"0+$distance-$desc"
    }.getOrElse {
      modifiedSuffix(sep = false, ts, date)
    }
  }

}
