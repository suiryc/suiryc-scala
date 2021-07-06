package suiryc.scala.log.rolling

import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.rolling.helper.{CompressionMode, Compressor, FileNamePattern, RenameUtil}
import ch.qos.logback.core.rolling.{RollingPolicyBase, RolloverFailure, FixedWindowRollingPolicy => lFixedWindowRollingPolicy}
import suiryc.scala.akka.CoreSystem

import java.io.File
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Failure

object FixedWindowRollingPolicy {

  // Notes:
  // The original FixedWindowRollingPolicy does compress log file synchronously
  // when triggered, which forcibly halts application code that does log
  // messages if not using an AsyncAppender.
  // Using AsyncAppender has a few drawbacks:
  //  - it has a configured queue limit of log events to retain in memory when
  //    underlying appender does not process them fast enough
  //  - using a too big queue could lead to OOMs
  //  - we will want to disable its default mechanism that drops events of level
  //    INFO and below when queue is almost full (80%)
  //  - queue will block when full
  //  - passing all caller data, if needed, to queued events is expensive
  //
  // Alternatively we can override the nominal rolling policy with a simple
  // mechanism doing compression in the background, similarly to what the
  // TimeBasedRollingPolicy natively handles. When rollover is triggered,
  // nominal archive files renaming is performed:
  //  - logfile.<maxIndex>.gz is removed
  //  - logfile.<maxIndex-1>.gz is renamed logfile.<maxIndex>.gz
  //  - ...
  //  - logfile.<minIndex>.gz is renamed logfile.<minIndex+1>.gz
  // Then instead of synchronously compressing logfile into
  // logfile.<minIndex>.gz:
  // 1. Code waits for previous compression to finish
  // 2. Active logfile is renamed into logfile.compressing
  // 3. logfile.compressing is asynchronously compressed into
  //    logfile.<minIndex>.gz, and removed once done
  // 4. Rollover code returns to caller, allowing application to log into a
  //    brand new logfile
  // This gives a chance for compression to be done before next rollover, while
  // blocking next rollover if previous compression is not yet done.
  // For complete handling:
  //  - logback stopping waits for previous compression to finish
  //  - when starting, if logfile.compressing still exists, it is first
  //    compressed asynchronously
  //
  // The Compressor code enforces ".gz" extension for compressed file.
  // It also does nothing if target file exists.

  // Access to the package-private compressor field.
  private val compressorField = {
    val field = classOf[lFixedWindowRollingPolicy].getDeclaredField("compressor")
    field.setAccessible(true)
    field
  }

  def getCompressor(obj: lFixedWindowRollingPolicy): Compressor = {
    compressorField.get(obj).asInstanceOf[Compressor]
  }

  def setCompressor(obj: lFixedWindowRollingPolicy, v: Compressor): Unit = {
    compressorField.set(obj, v)
  }

  // Access to the package-private fileNamePattern field.
  private val fileNamePatternField = {
    val field = classOf[RollingPolicyBase].getDeclaredField("fileNamePattern")
    field.setAccessible(true)
    field
  }

  def getFileNamePattern(obj: lFixedWindowRollingPolicy): FileNamePattern = {
    fileNamePatternField.get(obj).asInstanceOf[FileNamePattern]
  }

  /** Gzip compressor doing one async job at a time. */
  class GzipAsyncCompressor(util: RenameUtil, compressionMode: CompressionMode, activeFileName: String, firstCompressedFile: String)
    extends Compressor(compressionMode)
  {

    // Note: it is preferable not to use logging here, since we are used by
    // loggers. We have to use the addInfo/addWarn/addError methods that add
    // status events which are logged by status manager/listener if needed.

    // Previous compression.
    private var compressing: Option[Future[Unit]] = None

    def start(): Unit = {
      val nameCompressing = getCompressingName(activeFileName)
      // If the temporary compressing file exists, assume the JVM was previously
      // stopped before finishing compressing: do it now, in the background.
      if (new File(nameCompressing).exists()) {
        addWarn(s"Re-compressing first archived $nameCompressing log file")
        val target = new File(firstCompressedFile)
        if (target.exists()) target.delete()
        compressing = Some(blockingAsyncCompress(nameCompressing, firstCompressedFile))
      }
    }

    override def compress(nameOfFile2Compress: String, nameOfCompressedFile: String, innerEntryName: String): Unit = {
      // Wait for previous compression to finish.
      waitCompressed(Duration.Inf)
      compressing = Some {
        val nameCompressing = getCompressingName(nameOfFile2Compress)
        // There is no guarantee that:
        //  - we could properly delete temporary file after previous compression
        //  - something would inadvertently create a file with the same name
        //  - util.rename will remove the target file if existing
        // So as extra safety, first try to remove the target if it exists.
        if (new File(nameCompressing).exists()) {
          addWarn(s"Pre-existing $nameCompressing temporary archived log file will be deleted")
          tryDelete(nameCompressing, "pre-existing temporary archived")
        }
        util.rename(nameOfFile2Compress, nameCompressing)
        blockingAsyncCompress(nameCompressing, nameOfCompressedFile)
      }
    }

    private def getCompressingName(name: String): String = s"$name.compressing"

    private def tryDelete(file: String, kind: String): Unit = {
      try {
        new File(file).delete()
      } catch {
        case ex: Exception =>
          addError(s"Failed to delete $kind $file log file:", ex)
      }
      ()
    }

    private def blockingAsyncCompress(src: String, dst: String): Future[Unit] = {
      // Note: don't use asyncCompress, but create our own Future because we
      // have some more code to execute when compression is done.
      import suiryc.scala.concurrent.RichFuture
      RichFuture.blockingAsync {
        // scalastyle:off null
        super.compress(src, dst, null)
        // scalastyle:on null
        tryDelete(src, "temporary archived")
      }.andThen {
        case Failure(ex) =>
          addError("Failed to properly finish log compression", ex)
      }(CoreSystem.Blocking.dispatcher)
    }

    def waitCompressed(atMost: Duration): Unit = {
      val current = compressing
      compressing = None
      current.foreach { f =>
        if (!f.isCompleted) addWarn("Waiting for previous log compression to finish")
        try {
          Await.result(f, atMost)
        } catch {
          case ex: Exception =>
            throw new RolloverFailure("Failed to properly finish log compression", ex)
        }
      }
    }

  }

}

class FixedWindowRollingPolicy extends lFixedWindowRollingPolicy {

  import FixedWindowRollingPolicy._

  // Notes:
  // To limit code re-implementation, we use original class fields as much as
  // possible (even through reflection for non-public ones).

  // Tracking the min index does not require reflection:
  //  - the default value is 1
  //  - setMinIndex is called to change it
  private var _minIndex = 1

  private var _compressor: Option[GzipAsyncCompressor] = None

  override def start(): Unit = {
    super.start()
    // We only work for gz compression.
    if (compressionMode == CompressionMode.GZ) {
      // Replace the original compressor by our one.
      val util = new RenameUtil()
      util.setContext(context)
      val fileNamePattern = FixedWindowRollingPolicy.getFileNamePattern(this)
      val compressor = new GzipAsyncCompressor(util, compressionMode, getActiveFileName, fileNamePattern.convertInt(_minIndex))
      compressor.setContext(context)
      compressor.start()
      _compressor = Some(compressor)
      setCompressor(this, compressor)
    }
  }

  override def stop(): Unit = {
    // Wait for compressor to be done.
    _compressor.foreach { c =>
      try {
        // Wait as long as TimeBasedRollingPolicy.
        c.waitCompressed(CoreConstants.SECONDS_TO_WAIT_FOR_COMPRESSION_JOBS.seconds)
      } catch {
        case ex: Exception =>
          addError("Failed to finish archived log compression:", ex)
      }
    }
    super.stop()
  }

  override def rollover(): Unit = {
    super.rollover()
  }

  override def setMinIndex(minIndex: Int): Unit = {
    super.setMinIndex(minIndex)
    _minIndex = minIndex
  }

}
