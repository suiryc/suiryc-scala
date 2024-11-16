package suiryc.scala.sys.process

import suiryc.scala.akka.CoreSystem
import suiryc.scala.sys.OS

import java.io.{File, FilterInputStream, InputStream, OutputStream}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.sys.process.ProcessIO

// Notes:
// To handle Process execution scala offers scala.sys.process.Process, which
// relies on scala.sys.process.ProcessBuilderImpl.
// In particular caller passes a ProcessIO object holding code to execute to
// handle stdin/stdout/stderr; this code is executed in dedicated threads.
// Upon running, a scala.sys.process.ProcessImpl is created wrapping the
// java.lang.ProcessImpl which actually does Process forking and handling.
//
// (At least) On Linux with Java 11 - and scala 2.12/2.13 - a deadlock is
// triggered in the following conditions:
//  - (apparently) forked process did not close stdout or stderr
//  - passed ProcessIO code does read on stdout or stderr before the process
//    ended *and* there is actually nothing to read
// This could be consistently observed with qemu-nbd 4.1/4.2 (4.0 was fine).
//
// What happens:
// The java ProcessImpl uses ProcessPipeInputStream to wrap PipeInputStream
// used to read stdout and stderr file descriptors created upon forking.
// While the process is running, reading is blocking and holds a lock
// (synchronized for concurrent access).
// Once the process ends, ProcessImpl does retrieve the exit code, notifies
// threads waiting on it, and proceeds to end the ProcessPipeInputStream:
//  - the stream is drained
//  - if there was no data, the underlying PipeInputStream is replaced by a
//    singleton instance java.lang.ProcessBuilder.NullInputStream
//  - otherwise it is replaced by a ByteArrayInputBuffer wrapping drained data
// But the ProcessPipeInputStream lock is required to do this.
// Thus, if the ProcessIO code is blocked on reading (no data and the stream was
// not closed), ProcessImpl is also blocked handling the process ending.
//
// In order to get the exit value the scala ProcessImpl does wait for the java
// ProcessImpl to end then for ProcessIO threads to end too. It is thus also
// blocked in the above situation.
//
// To work around this, we can re-implement the scala ProcessBuilder/Process,
// and insert a (non-efficient) intermediate InputStream between the underlying
// ProcessPipeInputStream and the caller handling (ProcessIO) stdout/stderr.
// This intermediate stream would not directly read (blocking) while process is
// running, but always check data availability before doing so:
//  - if data are 'available', 'read' is called
//  - otherwise code goes to sleep for a given duration, before checking again
//  - if process ending has been notified (and there is no 'available' data)
//    EOS (-1) is returned
// Upon retrieving exit value, we wait for the ProcessImpl to end, then notifies
// (waking up too) the stdout/stderr ProcessIO threads before waiting for them
// to end too.
//
// This works because:
//  - NullInputStream is EOS and does not block
//  - ByteArrayInputBuffer does not block and always returns how many bytes are
//    still available
//  - ProcessPipeInputStream is also not expected to return 0 *if* there are
//    data remaining
//    - as a BufferInputStream, it returns how many bytes it has buffered plus
//      how many bytes are available from underlying stream
//    - PipeInputStream, as a FileInputStream, is expected to return 0 only
//      when beyond EOF
//    - the data draining code also fully rely on how many bytes the underlying
//      stream indicates as available
//
// It is necessary to check availability even after Process has ended because we
// are notified of it right before ProcessImpl does end ProcessPipeInputStream:
// there may be race condition as if we read before the underlying stream is
// replaced, then we would deadlock if there is actually no data available.
//
// This cannot be done on caller side (which manages the actual ProcessIO code
// being executed) because it relies on the fact we adapt the I/O behaviour
// depending on whether the Process has ended, and the deadlock prevents caller
// to know it.
//
// Alternatively using reflection to detect when underlying stream has been
// replaced works, but is discouraged since Java 9 and triggers warnings for
// 'Illegal reflective access by $PERPETRATOR to $VICTIM'.

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

  // Similarly to InterruptibleInputStream, we prevent 'read' blocking by
  // checking data are available before, and wait until we can read or Process
  // has ended.
  private[process] class NonBlockingInputStream(is: InputStream, loopDelay: FiniteDuration = 10.millis)
    extends FilterInputStream(is)
  {

    // Whether Process has ended.
    @volatile
    private var done = false

    // Notifies Process has ended.
    // Wakes up 'read' waiting for available data.
    def setDone(): Unit = synchronized {
      done = true
      notifyAll()
    }

    @inline
    private def nonblocking(f: => Int): Int = synchronized {
      @scala.annotation.tailrec
      def loop(): Int = {
        if (is.available() > 0) {
          // Only read when data are available, to prevent blocking and
          // workaround the deadlock.
          f
        } else if (done) {
          // If there is no data available *and* Process is done, we reached
          // EOS (data draining also relies on 'available').
          -1
        } else {
          // Wait a bit before checking again.
          // We either timeout or wake up notified once Process has ended.
          try {
            wait(loopDelay.toMillis)
          } catch {
            case _: InterruptedException =>
          }
          loop()
        }
      }

      loop()
    }

    override def read(): Int = {
      nonblocking(super.read())
    }

    override def read(b: Array[Byte], off: Int, len: Int): Int = {
      nonblocking(super.read(b, off, len))
    }

  }

}

class SimpleProcessBuilder(builder: ProcessBuilder) {

  import SimpleProcessBuilder._

  def run(io: ProcessIO): SimpleProcess = {
    // Actually start the process
    val process = builder.start()

    // This is where we deviate from scala Process code.
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

    new SimpleProcess(process, Some(inThread), outThread :: errorThread, List(outStream, errorStream))
  }

}

class SimpleProcess(val process: Process, inputThread: Option[Thread], outputThreads: List[Thread], streams: List[InputStream]) {

  import SimpleProcessBuilder.NonBlockingInputStream

  def destroy(forcibly: Boolean = false): Unit = {
    if (forcibly) {
      process.destroyForcibly()
      ()
    } else {
      process.destroy()
    }
  }

  def exitValue(): Int = {
    Await.result(exitValueAsync(), Duration.Inf)
  }

  def exitValueAsync(): Future[Int] = {
    import scala.jdk.FutureConverters._
    import CoreSystem.Blocking._
    process.onExit().asScala.transform { t =>
      // Notify input thread it can terminate
      inputThread.foreach(_.interrupt())
      // Notify stdout/stderr handler when Process has ended.
      streams.foreach {
        case s: NonBlockingInputStream => s.setDone()
        case _ =>
      }
      // Wait for output completion before returning the process exit code.
      outputThreads.foreach(_.join())
      t
    }.map { _ =>
      process.exitValue()
    }
  }

}

// Notes:
// Proper class, not static object, because at least null I/O streams are meant
// to be per-process and not shared.
// This also allows caller to decide whether the process is failed or not.
/** Dummy process. */
class DummyJProcess(failed: Boolean = false) extends Process {
  override def getOutputStream: OutputStream = OutputStream.nullOutputStream()
  override def getInputStream: InputStream = InputStream.nullInputStream()
  override def getErrorStream: InputStream = InputStream.nullInputStream()
  override def waitFor(): Int = if (failed) 1 else 0
  override def exitValue(): Int = if (failed) 1 else 0
  override def destroy(): Unit = {}
}

/** Dummy process. */
class DummyProcess(failed: Boolean = false)
  extends SimpleProcess(new DummyJProcess(failed), None, Nil, Nil)
