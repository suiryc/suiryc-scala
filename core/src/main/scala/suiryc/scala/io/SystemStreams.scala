package suiryc.scala.io

import com.typesafe.scalalogging.Logger
import java.io.{InputStream, PrintStream}
import java.nio.charset.StandardCharsets

/** System streams (stdin, stdout, stderr). */
class SystemStreams(val in: InputStream, val out: PrintStream, val err: PrintStream)

object SystemStreams {

  /** /dev/null output equivalent. */
  val NullOutput = new PrintStream((_: Int) => {})

  /** Gets current streams. */
  def apply(): SystemStreams =
    SystemStreams(System.in, System.out, System.err)

  def apply(in: InputStream, out: PrintStream, err: PrintStream): SystemStreams =
    new SystemStreams(in, out, err)

  /** Creates a PrintStream that logs written lines. */
  def loggerOutput(loggerName: String, error: Boolean = false): PrintStream = {
    val logger = Logger(loggerName)
    val out = new LineSplitterOutputStream(new LogLineWriter(logger, error), StandardCharsets.UTF_8)
    new PrintStream(out, false, StandardCharsets.UTF_8)
  }

  /**
   * Replace requested streams.
   *
   * @param in new stdin, or None
   * @param out new stdout, or None
   * @param err new stderr, or None
   * @return initial streams
   */
  def replace(in: Option[InputStream] = None, out: Option[PrintStream] = None, err: Option[PrintStream] = None): SystemStreams = {
    val previous = SystemStreams()

    in.foreach(System.setIn)
    out.foreach { out =>
      System.out.flush()
      System.setOut(out)
    }
    err.foreach { err =>
      System.err.flush()
      System.setErr(err)
    }

    previous
  }

  /** Replace stdin. */
  def replace(input: InputStream): SystemStreams =
    replace(in = Some(input))

  /** Replace stdout. */
  def replace(output: PrintStream): SystemStreams =
    replace(out = Some(output), err = Some(output))

  /** Replace stdout and stderr. */
  def replace(out: PrintStream, err: PrintStream): SystemStreams =
    replace(out = Some(out), err = Some(err))

  /** Replace stdin, stdout and stderr. */
  def replace(in: InputStream, out: PrintStream, err: PrintStream): SystemStreams =
    replace(in = Some(in), out = Some(out), err = Some(err))

  /** Restore system streams. */
  def restore(streams: SystemStreams): Unit = {
    replace(streams.in, streams.out, streams.err)
    ()
  }

  private class LogLineWriter(logger: Logger, error: Boolean) extends LineWriter {
    override def write(line: String): Unit = {
      if (error) logger.error("{}", line)
      else logger.info("{}", line)
    }
  }

}
