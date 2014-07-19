package suiryc.scala.io

import java.io.{InputStream, PrintStream}


class SystemStreams(val in: InputStream, val out: PrintStream, val err: PrintStream)

object SystemStreams {

  def apply(in: InputStream, out: PrintStream, err: PrintStream) =
    new SystemStreams(in, out, err)

  def replace(in: Option[InputStream] = None, out: Option[PrintStream] = None, err: Option[PrintStream] = None): SystemStreams = {
    val previous = SystemStreams(System.in, System.out, System.err)

    in foreach(System.setIn)
    out foreach { out =>
      System.out.flush()
      System.setOut(out)
    }
    err foreach { err =>
      System.err.flush()
      System.setErr(err)
    }

    previous
  }

  def replace(output: PrintStream): SystemStreams =
    replace(in = None, out = Some(output), err = Some(output))

  def replace(out: PrintStream, err: PrintStream): SystemStreams =
    replace(in = None, out = Some(out), err = Some(err))

  def replace(in: InputStream, out: PrintStream, err: PrintStream): SystemStreams =
    replace(in = Some(in), out = Some(out), err = Some(err))

  def restore(streams: SystemStreams) {
    replace(streams.in, streams.out, streams.err)
  }

}
