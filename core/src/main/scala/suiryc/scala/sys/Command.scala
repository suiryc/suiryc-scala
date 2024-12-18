package suiryc.scala.sys

import com.typesafe.scalalogging.StrictLogging
import suiryc.scala.akka.CoreSystem
import suiryc.scala.io.{IOStream, InterruptibleInputStream}
import suiryc.scala.misc.RichOptional._
import suiryc.scala.misc.Util
import suiryc.scala.sys.process.SimpleProcess

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, IOException, InputStream, OutputStream}
import java.nio.file.{Path, Paths}
import scala.annotation.nowarn
import scala.collection.compat._
import scala.collection.compat.immutable.LazyList
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.sys.process.{BasicIO, ProcessIO}

/**
 * Command execution result.
 *
 * @param exitCode exit code
 * @param stdout captured stdout, or empty string
 * @param stderr captured stderr, or empty string
 */
case class CommandResult(
  exitCode: Int,
  stdout: String,
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
  extends StrictLogging
{

  val bufferSize = 1024

  /** Command output type. */
  object OutputType extends Enumeration {
    /** stdout output */
    val stdout: OutputType.Value = Value
    /** stderr output */
    val stderr: OutputType.Value = Value
    /** stdout and stderr output */
    val both: OutputType.Value = Value
  }

  /** Command input/output stream. */
  class Stream[+T](val stream: T, val close: Boolean)

  /** stdin as input stream (made interruptible) */
  // Note: since we remember whether we were interrupted, we need to create a
  // new Stream for each command to execute, hence 'def' instead of 'val'.
  def fromStdin: Option[Stream[InputStream]] = input(scala.sys.process.stdin, close = false, makeInterruptible = true)

  /** stdout as output stream */
  val toStdout: Option[Stream[OutputStream]] = output(scala.sys.process.stdout, close = false)

  /** stderr as output stream */
  val toStderr: Option[Stream[OutputStream]] = output(scala.sys.process.stderr, close = false)

  /** stdout sink added for each executed command */
  private var extraStdoutSink = mutable.ListBuffer[Stream[OutputStream]]()

  /** stderr sink added for each executed command */
  private var extraStderrSink = mutable.ListBuffer[Stream[OutputStream]]()

  /**
   * Adds a stdout/stderr sink.
   *
   * @param os where the sink sends the output
   */
  def addExtraOutputSink(os: OutputStream, kind: OutputType.Value = OutputType.both): Unit = {
    // @nowarn workarounds scala 2.13.x false-positive
    val sinks = (kind: @nowarn) match {
      case OutputType.stdout => List(extraStdoutSink)
      case OutputType.stderr => List(extraStderrSink)
      case OutputType.both => List(extraStdoutSink, extraStderrSink)
    }

    sinks.foreach { sink =>
      sink += new Stream(os, false)
    }
  }

  /**
   * Removes a stdout/stderr sink.
   *
   * @param os where the sink was sending the output
   */
  def removeExtraOutputSink(os: OutputStream, kind: OutputType.Value = OutputType.both): Unit = {
    def filter(sink: mutable.ListBuffer[Stream[OutputStream]]): mutable.ListBuffer[Stream[OutputStream]] =
      sink.filterNot { stream =>
        stream.stream eq os
      }

    // @nowarn workarounds scala 2.13.x false-positive
    (kind: @nowarn) match {
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
   * @param makeInterruptible whether to make the stream interruptible
   */
  def input(is: InputStream, close: Boolean = true, makeInterruptible: Boolean = false): Option[Stream[InputStream]] = {
    if (makeInterruptible) Some(new Stream(new InterruptibleInputStream(is), close))
    else Some(new Stream(is, close))
  }

  /**
   * Creates command input stream.
   *
   * @param bytes bytes to send
   */
  def input(bytes: Array[Byte]): Option[Stream[ByteArrayInputStream]] =
    Some(new Stream(new ByteArrayInputStream(Util.wrapNull(bytes)), close = true))

  /**
   * Creates command output stream.
   *
   * @param os    stream to connect
   * @param close whether to close stream once finished
   */
  def output(os: OutputStream, close: Boolean = true): Option[Stream[OutputStream]] =
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
  // scalastyle:off parameter.number
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
  ): CommandResult = {
    Await.result(
      executeAsync(
        cmd = cmd,
        workingDirectory = workingDirectory,
        envf = envf,
        stdinSource = stdinSource,
        stdoutSink = stdoutSink,
        captureStdout = captureStdout,
        stderrSink = stderrSink,
        captureStderr = captureStderr,
        trim = trim,
        skipResult = skipResult
      )._2,
      Duration.Inf
    )
  }
  // scalastyle:on parameter.number

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
  // scalastyle:off method.length parameter.number
  def executeAsync(
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
  ): (SimpleProcess, Future[CommandResult]) = {
    def _filterOutput(
      input: InputStream,
      outputs: Iterable[Stream[OutputStream]]
    ): Unit = {
      val buffer = new Array[Byte](bufferSize)

      LazyList.continually(input.read(buffer)).takeWhile { read =>
        read != -1
      }.foreach { read =>
        outputs.foreach { _.stream.write(buffer, 0, read) }
      }

      IOStream.closeQuietly(input)
      outputs.foreach { output =>
        output.stream.flush()
        if (output.close) IOStream.closeQuietly(output.stream)
      }
    }

    def filterOutput(sink: Iterable[Stream[OutputStream]], buffer: Option[StringBuffer])
      (input: InputStream): Unit =
    {
      val tee = buffer.map { _ =>
        new Stream(new ByteArrayOutputStream(bufferSize), true)
      }

      _filterOutput(input, sink ++ tee)

      buffer.foreach { buffer =>
        buffer.append(tee.get.stream.toString)
      }
    }

    def filterInput(input: Stream[InputStream], output: OutputStream): Unit = {
      val buffer = new Array[Byte](bufferSize)

      @scala.annotation.tailrec
      def loop(): Unit = {
        // If input stream has been closed, we get an IOException with
        // "Stream closed" message.
        // Transform this into EOF.
        val read = try {
          input.stream.read(buffer)
        } catch {
          case ex: IOException if ex.getMessage == "Stream closed" =>
            -1
        }
        if (read == -1) {
          if (input.close) IOStream.closeQuietly(input.stream)
          IOStream.closeQuietly(output)
          ()
        } else {
          output.write(buffer, 0, read)
          // flush will throw an exception once the process has terminated
          val available = try {
            output.flush()
            true
          } catch {
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
    val builder = process.SimpleProcessBuilder(cmd, workingDirectory, envf)
    val io = new ProcessIO(
      stdinSource.fold[OutputStream => Unit](BasicIO.close)(input => filterInput(input, _)),
      filterOutput(stdoutSink ++ extraStdoutSink, stdoutBuffer),
      filterOutput(stderrSink ++ extraStderrSink, stderrBuffer)
    )
    val simpleProcess = builder.run(io)

    val fr = simpleProcess.exitValueAsync().map { result =>
      val stdoutResult =
        stdoutBuffer.fold("")(buffer => buffer.toString.optional(trim, _.trim))
      val stderrResult =
        stderrBuffer.fold("")(buffer => buffer.toString.optional(trim, _.trim))

      if (!skipResult && (result != 0)) {
        logger.error(s"Command[$cmd] failed: code[$result]"
          + stdoutBuffer.fold("")(_ => s" stdout[$stdoutResult]")
          + stderrBuffer.fold("")(_ => s" stderr[$stderrResult]")
        )
        throw new RuntimeException("Nonzero exit value: " + result)
      } else {
        logger.trace(s"Command[$cmd] result: code[$result]"
          + stdoutBuffer.fold("")(_ => s" stdout[$stdoutResult]")
          + stderrBuffer.fold("")(_ => s" stderr[$stderrResult]")
        )
      }

      // Process may have ended before consuming the whole input
      stdinSource.foreach { input =>
        if (input.close) IOStream.closeQuietly(input.stream)
      }

      CommandResult(result, stdoutResult, stderrResult)
    }(CoreSystem.Blocking.dispatcher)

    (simpleProcess, fr)
  }
  // scalastyle:on method.length parameter.number

  /**
   * Locates command.
   *
   * If given command is an absolute path, it is returned as-is if it exists.
   * For non-absolute path, the appropriate OS tool is used to locate the
   * command, which is returned if found.
   *
   * @param cmd command to locate
   * @return absolute command path; None if it does not exist
   */
  def locate(cmd: String): Option[Path] = {
    val path = Paths.get(cmd)
    if (path.isAbsolute) {
      Option.when(path.toFile.exists())(path)
    } else {
      val args = if (OS.isWindows) {
        List("where", cmd)
      } else {
        // Inside bash, "command -v" is also available. But since this is an
        // internal command, we cannot use it from our side: we need to call
        // an existing external command, thus "which" is the way to go here.
        List("which", cmd)
      }
      val r = Command.execute(
        cmd = args,
        stdinSource = None,
        stdoutSink = None,
        stderrSink = None
      )
      if (r.exitCode == 0) {
        Some(Paths.get(r.stdout))
      } else {
        None
      }
    }
  }

}
