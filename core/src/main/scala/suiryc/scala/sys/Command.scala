package suiryc.scala.sys

import grizzled.slf4j.Logging
import java.io.{
  BufferedReader,
  BufferedWriter,
  File,
  InputStream,
  InputStreamReader,
  OutputStream,
  OutputStreamWriter,
  Reader,
  Writer
}
import scala.sys.process._
import suiryc.scala.misc.RichOptional._

/** Command execution result. */
case class CommandResult(
  /** Exit code */
  exitCode: Int,
  /** Captured stdout, or empty string */
  stdout: String,
  /** Captured stderr, or empty string */
  stderr: String
)


object Command
  extends Logging
{

  /**
   * Executes system command.
   *
   * @param cmd              command to perform
   * @param workingDirectory working directory
   * @param envf             environment callback
   * @param captureStdout    whether to capture stdout
   * @param printStdout      whether to print stdout
   * @param captureStderr    whether to capture stderr
   * @param printStderr      whether to print stderr
   * @param trim             whether to trim captured streams
   * @param skipResult       whether to not check return code
   * @return command result
   */
  def execute(
      cmd: Seq[String],
      workingDirectory: Option[File] = None,
      envf: Option[java.util.Map[String, String] => Unit] = None,
      captureStdout: Boolean = true,
      printStdout: Boolean = false,
      captureStderr: Boolean = true,
      printStderr: Boolean = false,
      trim: Boolean = true,
      skipResult: Boolean = true
    ): CommandResult =
  {
    @annotation.tailrec
    def _filterOutput(
      reader: Reader,
      writer: Option[Writer],
      buffer: Option[StringBuffer]
    ) {
      val tmp = new Array[Char](1024)
      val read = reader.read(tmp)
      if (read == -1) {
        reader.close()
        /* Notes:
         *   - since we are using stdout/stderr as output, DO NOT close it
         *   - since we are wrapping the output (BufferedWriter), DO flush it
         */
        writer foreach { _.flush() }
      }
      else {
        /* Note: using a mutable StringBuffer should be more efficient (compared
         * to concatenating to an accumulator string). */
        buffer foreach { _.append(tmp, 0, read) }
        writer foreach { _.write(tmp, 0, read) }
        _filterOutput(reader, writer, buffer)
      }
    }

    def filterOutput(buffer: Option[StringBuffer], output: Option[OutputStream])
      (input: InputStream)
    {
      val reader = new BufferedReader(new InputStreamReader(input))
      val writer = output map { output =>
        new BufferedWriter(new OutputStreamWriter(output))
      }

      _filterOutput(reader, writer, buffer)
    }

    def handleOutput(
        capture: Boolean,
        buffer: StringBuffer,
        print: Boolean,
        output: OutputStream
      ): (InputStream) => Unit =
    {
      /* Note: some programs appear to fail if output is closed, so trash it if
       * necessary, but don't close it. */
      if (!capture && print)
        BasicIO.transferFully(_, output)
      else
        filterOutput(
          if (capture) Some(buffer) else None,
          if (print) Some(output) else None
        )
    }

    val stdoutBuffer = new StringBuffer()
    val stderrBuffer = new StringBuffer()
    val process = envf map { f =>
      /* scala Process only handles adding variables, so - as Process - build
       * the java ProcessBuilder, and let callback adapt its environment.
       */
      val jpb = new java.lang.ProcessBuilder(cmd.toArray: _*)
      workingDirectory foreach (jpb directory _)
      f(jpb.environment())
      Process(jpb)
    } getOrElse(Process(cmd, workingDirectory))
    val io = new ProcessIO(
      BasicIO.close,
      handleOutput(captureStdout, stdoutBuffer, printStdout, stdout),
      handleOutput(captureStderr, stderrBuffer, printStderr, stderr)
    )
    val result = process.run(io).exitValue

    if (!skipResult && (result != 0)) {
      trace(s"Command[$cmd] failed: code[$result]"
        + (if (!printStdout && captureStdout) s" stdout[$stdoutBuffer]" else "")
        + (if (!printStderr && captureStderr) s" stderr[$stderrBuffer]" else "")
      )
      throw new RuntimeException("Nonzero exit value: " + result)
    }
    else {
      trace(s"Command[$cmd] result: code[$result]"
        + (if (!printStdout && captureStdout) s" stdout[$stdoutBuffer]" else "")
        + (if (!printStderr && captureStderr) s" stderr[$stderrBuffer]" else "")
      )
    }

    CommandResult(result,
      stdoutBuffer.toString.optional(trim, _.trim),
      stderrBuffer.toString.optional(trim, _.trim)
    )
  }

}
