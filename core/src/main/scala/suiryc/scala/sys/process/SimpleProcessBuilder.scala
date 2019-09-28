package suiryc.scala.sys.process

import java.io.{File, FilterInputStream, InputStream}
import scala.concurrent.duration._
import scala.sys.process.ProcessIO
import suiryc.scala.sys.OS

/**
 * Simple process builder.
 *
 * Borrows most code from scala Process, leaving out the chaining stuff.
 * Only handles creating builder and starting process, with some extra stuff.
 */
object SimpleProcessBuilder {

  def apply(
    cmd: Seq[String],
    workingDirectory: Option[File] = None,
    envf: Option[java.util.Map[String, String] => Unit] = None): SimpleProcessBuilder =
  {
    // scala Process only handles adding variables, so - as does Process - build
    // the java ProcessBuilder, and let callback adapt its environment.
    val jpb = new java.lang.ProcessBuilder(cmd.toArray: _*)
    workingDirectory.foreach(jpb.directory)
    envf.foreach(_.apply(jpb.environment()))
    new SimpleProcessBuilder(jpb)
  }

  private object Spawn {

    def apply(f: => Unit, daemon: Boolean): Thread = {
      val thread = new Thread() {
        override def run(): Unit = {
          f
        }
      }
      thread.setDaemon(daemon)
      thread.start()
      thread
    }

  }

  // Similarly to InterruptibleInputStream, we prevent 'read' blocking, until
  // being told we can.
  private[process] class NonBlockingInputStream(is: InputStream, loopDelay: FiniteDuration = 50.millis)
    extends FilterInputStream(is)
  {

    @volatile
    private var canBlock = false

    def setCanBlock(): Unit = {
      canBlock = true
    }

    @inline
    private def interruptible(f: => Int): Int = {
      @scala.annotation.tailrec
      def loop(): Int = {
        if (canBlock || (is.available() > 0)) f
        else {
          try {
            Thread.sleep(loopDelay.toMillis)
          } catch {
            case _: InterruptedException =>
          }
          loop()
        }
      }

      loop()
    }

    override def read(): Int = {
      interruptible(super.read())
    }

    override def read(b: Array[Byte], off: Int, len: Int): Int = {
      interruptible(super.read(b, off, len))
    }

  }

}

class SimpleProcessBuilder(builder: ProcessBuilder) {

  import SimpleProcessBuilder._

  def run(io: ProcessIO): SimpleProcess = {
    // Actually start the process
    val process = builder.start()

    // Handle process stdin/stdout/stderr in dedicated threads.
    // Notes:
    // This is where we deviate from scala Process code.
    // Given the following situation:
    //  - running on Linux (observed there)
    //  - reading stdout/stderr (observed on stderr specifically, but there is
    //    no reason it would not happen on stdout too) concurrently to process
    //    running
    //  - spawn process did not write anything to stdout/stderr
    // Then sometimes (maybe in a reproducible manner; e.g. if the process
    // does not end almost immediately ?) we reach a deadlock:
    //   - 'read' is blocked (nothing to get, and stream not closed) while
    //     holding the stream 'synchronized' lock
    //   - Process code is waiting for the stream 'synchronized' lock in order
    //     to drain the data and close the stream
    // To workaround this, we have to create an non-efficient intermediate
    // stream that does not block until process is done running.
    val isLinux = OS.isLinux
    def wrapStream(is: InputStream): InputStream = {
      if (isLinux) new NonBlockingInputStream(is)
      else is
    }

    val inThread  = Spawn(io.writeInput(process.getOutputStream), daemon = true)
    val outStream = wrapStream(process.getInputStream)
    val outThread = Spawn(io.processOutput(outStream), io.daemonizeThreads)
    val errorStream = wrapStream(process.getErrorStream)
    val errorThread =
      if (builder.redirectErrorStream) Nil
      else List(Spawn(io.processError(errorStream), io.daemonizeThreads))

    new SimpleProcess(process, inThread, outThread :: errorThread, List(outStream, errorStream))
  }
}

class SimpleProcess(process: Process, inputThread: Thread, outputThreads: List[Thread], streams: List[InputStream]) {

  import SimpleProcessBuilder.NonBlockingInputStream

  def exitValue(): Int = {
    // Wait for process to terminate.
    try {
      process.waitFor()
    } finally {
      // Notify input thread it can terminate
      inputThread.interrupt()
    }
    // Streams can do blocking read now.
    streams.foreach {
      case s: NonBlockingInputStream => s.setCanBlock()
      case _ =>
    }
    // Wait for output completion before returning the process exit code.
    outputThreads.foreach(_.join())
    process.exitValue()
  }

}
