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


object Command
  extends Logging
{

  /**
   * Executes system command.
   *
   * @param cmd              command to perform
   * @param workingDirectory working directory
   * @param captureStdout    whether to capture stdout
   * @param printStdout      whether to print stdout
   * @param captureStderr    whether to capture stderr
   * @param printStderr      whether to print stderr
   * @param skipResult       whether to not check return code
   * @return a tuple with the return code, stdout and stderr contents (empty
   *   unless captured)
   */
  def execute(
      cmd: Seq[String],
      workingDirectory: Option[File] = None,
      captureStdout: Boolean = true,
      printStdout: Boolean = false,
      captureStderr: Boolean = true,
      printStderr: Boolean = false,
      skipResult: Boolean = true
    ): (Int, String, String) =
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
    val process = Process(cmd, workingDirectory)
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

    (result, stdoutBuffer.toString, stderrBuffer.toString)
  }

}
