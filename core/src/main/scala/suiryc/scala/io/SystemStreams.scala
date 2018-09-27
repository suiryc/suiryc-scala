package suiryc.scala.io

import java.io.{InputStream, PrintStream}

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
    out.foreach { out ⇒
      System.out.flush()
      System.setOut(out)
    }
    err.foreach { err ⇒
      System.err.flush()
      System.setErr(err)
    }

    previous
  }

  /** Replace stdout. */
  def replace(output: PrintStream): SystemStreams =
    replace(in = None, out = Some(output), err = Some(output))

  /** Replace stdout and stderr. */
  def replace(out: PrintStream, err: PrintStream): SystemStreams =
    replace(in = None, out = Some(out), err = Some(err))

  /** Replace stdin, stdout and stderr. */
  def replace(in: InputStream, out: PrintStream, err: PrintStream): SystemStreams =
    replace(in = Some(in), out = Some(out), err = Some(err))

  /** Restore system streams. */
  def restore(streams: SystemStreams): Unit = {
    replace(streams.in, streams.out, streams.err)
    ()
  }

}
