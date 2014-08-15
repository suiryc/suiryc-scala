package suiryc.scala.sys

import grizzled.slf4j.Logging
import java.io.{
  ByteArrayInputStream,
  ByteArrayOutputStream,
  File,
  InputStream,
  IOException,
  OutputStream
}
import scala.collection.mutable
import scala.sys.process
import scala.sys.process.{BasicIO, Process, ProcessIO}
import suiryc.scala.misc.RichOptional._
import suiryc.scala.misc.Util

/** Command execution result. */
case class CommandResult(
  /** Exit code */
  exitCode: Int,
  /** Captured stdout, or empty string */
  stdout: String,
  /** Captured stderr, or empty string */
  stderr: String
)
{

  def toEither[F, S](failure: String => F, success: String => S): Either[F, S] =
    if (exitCode == 0) Right(success(stdout))
    else Left(failure(stderr))

  def toEither(errorMessage: String): Either[Exception, String] =
    toEither(
      f => new Exception(s"$errorMessage: $f"),
      s => s
    )

}


object Command
  extends Logging
{

  /** Command output type. */
  object OutputType extends Enumeration {
    /** stdout output */
    val stdout = Value
    /** stderr output */
    val stderr = Value
    /** stdout and stderr output */
    val both = Value
  }

  /** Command input/output stream. */
  class Stream[+T](val stream: T, val close: Boolean)

  /** stdin as input stream */
  val fromStdin = Some(new Stream(process.stdin, false))

  /** stdout as output stream */
  val toStdout = Some(new Stream(process.stdout, false))

  /** stderr as output stream */
  val toStderr = Some(new Stream(process.stderr, false))

  /** stdout sink added for each executed command */
  private var extraStdoutSink = mutable.ListBuffer[Stream[OutputStream]]()

  /** stderr sink added for each executed command */
  private var extraStderrSink = mutable.ListBuffer[Stream[OutputStream]]()

  /**
   * Adds a stdout/stderr sink.
   *
   * @param os where the sink sends the output
   */
  def addExtraOutputSink(os: OutputStream, kind: OutputType.Value = OutputType.both) {
    val sinks = kind match {
      case OutputType.stdout => List(extraStdoutSink)
      case OutputType.stderr => List(extraStderrSink)
      case OutputType.both => List(extraStdoutSink, extraStderrSink)
    }

    sinks foreach { sink =>
      sink += new Stream(os, false)
    }
  }

  /**
   * Removes a stdout/stderr sink.
   *
   * @param os where the sink was sending the output
   */
  def removeExtraOutputSink(os: OutputStream, kind: OutputType.Value = OutputType.both) {
    def filter(sink: mutable.ListBuffer[Stream[OutputStream]]): mutable.ListBuffer[Stream[OutputStream]] =
      sink filterNot { stream =>
        stream.stream eq os
      }

    val sinks = kind match {
      case OutputType.stdout =>
        extraStdoutSink = filter(extraStdoutSink)

      case OutputType.stderr =>
        extraStderrSink = filter(extraStderrSink)

      case OutputType.both =>
        extraStdoutSink = filter(extraStdoutSink)
        extraStderrSink = filter(extraStderrSink)
    }
  }

  /**
   * Creates command input stream.
   *
   * @param is    stream to connect
   * @param close whether to close stream once finished
   */
  def input(is: InputStream, close: Boolean = true) =
    Some(new Stream(is, close))

  /**
   * Creates command input stream.
   *
   * @param bytes bytes to send
   */
  def input(bytes: Array[Byte]) =
    Some(new Stream(new ByteArrayInputStream(Util.wrapNull(bytes)), true))

  /**
   * Creates command output stream.
   *
   * @param os    stream to connect
   * @param close whether to close stream once finished
   */
  def output(os: OutputStream, close: Boolean = true) =
    Some(new Stream(os, close))

  /**
   * Executes system command.
   *
   * @param cmd              command to execute
   * @param workingDirectory working directory
   * @param envf             environment callback
   * @param stdinSource      command input
   * @param stdoutSink       command stdout(s)
   * @param captureStdout    whether to capture stdout
   * @param stderrSink       command stderr(s)
   * @param captureStderr    whether to capture stderr
   * @param trim             whether to trim captured streams
   * @param skipResult       whether to not check return code
   * @return command result
   */
  def execute(
      cmd: Seq[String],
      workingDirectory: Option[File] = None,
      envf: Option[java.util.Map[String, String] => Unit] = None,
      stdinSource: Option[Stream[InputStream]] = fromStdin,
      stdoutSink: Iterable[Stream[OutputStream]] = None,
      captureStdout: Boolean = true,
      stderrSink: Iterable[Stream[OutputStream]] = None,
      captureStderr: Boolean = true,
      trim: Boolean = true,
      skipResult: Boolean = true
    ): CommandResult =
  {
    def _filterOutput(
      input: InputStream,
      outputs: Iterable[Stream[OutputStream]]
    ) {
      val buffer = new Array[Byte](1024)

      Stream.continually(input.read(buffer)) takeWhile { read =>
        read != -1
      } foreach { read =>
        outputs foreach { _.stream.write(buffer, 0, read) }
      }

      input.close()
      outputs foreach { output =>
        output.stream.flush()
        if (output.close) output.stream.close()
      }
    }

    def filterOutput(sink: Iterable[Stream[OutputStream]], buffer: Option[StringBuffer])
      (input: InputStream)
    {
      val tee = buffer map { _ =>
        new Stream(new ByteArrayOutputStream(256), true)
      }

      _filterOutput(input, sink ++ tee)

      buffer map { buffer =>
        buffer.append(tee.get.stream.toString)
      }
    }

    def filterInput(input: Stream[InputStream], output: OutputStream) {
      val buffer = new Array[Byte](1024)

      @scala.annotation.tailrec
      def loop() {
        val read = input.stream.read(buffer)
        if (read == -1) {
          if (input.close) input.stream.close()
          output.close()
        }
        else {
          output.write(buffer, 0, read)
          /* flush will throw an exception once the process has terminated */
          val available = try {
            output.flush()
            true
          }
          catch {
            case _: IOException => false
          }
          if (available) loop()
        }
      }

      loop()
    }

    val stdoutBuffer =
      if (captureStdout) Some(new StringBuffer())
      else None
    val stderrBuffer =
      if (captureStderr) Some(new StringBuffer())
      else None
    val process = envf.map { f =>
      /* scala Process only handles adding variables, so - as Process - build
       * the java ProcessBuilder, and let callback adapt its environment.
       */
      val jpb = new java.lang.ProcessBuilder(cmd.toArray: _*)
      workingDirectory foreach (jpb directory _)
      f(jpb.environment())
      Process(jpb)
    }.getOrElse(Process(cmd, workingDirectory))
    val io = new ProcessIO(
      stdinSource.fold[OutputStream => Unit](BasicIO.close)(input => filterInput(input, _)),
      filterOutput(stdoutSink ++ extraStdoutSink, stdoutBuffer),
      filterOutput(stderrSink ++ extraStderrSink, stderrBuffer)
    )
    val result = process.run(io).exitValue()
    val stdoutResult =
      stdoutBuffer.fold("")(buffer => buffer.toString.optional(trim, _.trim))
    val stderrResult =
      stderrBuffer.fold("")(buffer => buffer.toString.optional(trim, _.trim))

    if (!skipResult && (result != 0)) {
      error(s"Command[$cmd] failed: code[$result]"
        + stdoutBuffer.fold("")(buffer => s" stdout[$stdoutResult]")
        + stderrBuffer.fold("")(buffer => s" stderr[$stderrResult]")
      )
      throw new RuntimeException("Nonzero exit value: " + result)
    }
    else {
      trace(s"Command[$cmd] result: code[$result]"
        + stdoutBuffer.fold("")(buffer => s" stdout[$stdoutResult]")
        + stderrBuffer.fold("")(buffer => s" stderr[$stderrResult]")
      )
    }

    /* Process may have ended before consuming the whole input */
    stdinSource foreach { input =>
      if (input.close) input.stream.close()
    }

    CommandResult(result, stdoutResult, stderrResult)
  }

}
