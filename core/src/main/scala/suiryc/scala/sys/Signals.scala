package suiryc.scala.sys

//import jdk.internal.misc.Signal
import com.typesafe.scalalogging.StrictLogging
import sun.misc.{Signal, SignalHandler}

import java.util
import scala.collection.mutable

// Notes:
// sun.misc.Signal is legacy and relies on jdk.internal.misc.Signal, but the
// latter requires granting access. Since the official way to do this is is not
// supported in scala (at least up to 2.13.3) it has to be done e.g. by starting
// the JVM with: --add-exports java.base/jdk.internal.misc=ALL-UNNAMED
// For now simply keep on using the legacy class.

/**
 * Signals helpers.
 *
 * Allows registering and unregistering signal handlers.
 * Handlers execution is done in no particular order, and sequentially.
 * It is advised not to register too many handlers, nor having code that takes
 * a long time to do its job. In the latter, handler may delegate actual
 * execution to a dedicated thread running in the background, and should
 * properly handle being called again while previous execution is still
 * running.
 */
object Signals extends SignalHandler with StrictLogging {

  type Handler = SignalHandler

  val SIGHUP: Signal = new Signal("HUP")
  val SIGUSR1: Signal = new Signal("USR1")
  val SIGUSR2: Signal = new Signal("USR2")

  private val creationTime: Long = System.currentTimeMillis()

  // Notes:
  // We need to lock (synchronized) accesses to some variables in order
  // to cope with concurrent accesses. e.g. 'latest' and 'handlers'.
  // For this, we need a mutable collection.

  private val latest: mutable.Map[Signal, Long] = mutable.Map.empty

  private val handlers: mutable.Map[Signal, util.IdentityHashMap[Handler, String]] = mutable.Map.empty

  override def handle(sig: Signal): Unit = {
    logger.debug(s"Received $sig signal")
    val h = getHandlers(sig)
    updateLatest(sig)
    h.synchronized {
      val it = h.entrySet().iterator()

      @scala.annotation.tailrec
      def loop(): Unit = {
        if (it.hasNext) {
          val entry = it.next()
          try {
            entry.getKey.handle(sig)
          } catch {
            case ex: Exception =>
              logger.error(s"$sig handler=<${entry.getValue}> failed: ${ex.getMessage}")
          }
          loop()
        }
      }

      loop()
    }
  }

  private def setup(signal: Signal): Unit = {
    try {
      Signal.handle(signal, this)
      logger.debug(s"Installed $signal handler")
    } catch {
      case ex: Exception =>
        logger.warn(s"Failed to install $signal handler: ${ex.getMessage}")
    }
  }

  private def getHandlers(signal: Signal): util.IdentityHashMap[Handler, String] = handlers.synchronized {
    handlers.getOrElseUpdate(signal, {
      // Signal not intercepted yet.
      setup(signal)
      new util.IdentityHashMap[Handler, String]()
    })
  }

  private def updateLatest(signal: Signal): Unit = latest.synchronized {
    latest.update(signal, System.currentTimeMillis())
  }

  /** Gets the latest signal handling time. */
  def getLatest(signal: Signal): Long = latest.synchronized {
    latest.getOrElseUpdate(signal, creationTime)
  }

  /** Adds a signal handler. */
  def addHandler(signal: Signal, handler: Handler, desc: String): Unit = {
    val h = getHandlers(signal)
    h.synchronized {
      h.put(handler, desc)
    }
    ()
  }

  /** Removes a signal handler. */
  def removeHandler(signal: Signal, handler: Handler): Boolean = {
    val h = getHandlers(signal)
    h.synchronized {
      h.remove(handler) != null
    }
  }

}
