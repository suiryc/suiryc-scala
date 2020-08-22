package suiryc.scala.sys

//import jdk.internal.misc.Signal
import sun.misc.{Signal, SignalHandler}

// Notes:
// sun.misc.Signal is legacy and relies on jdk.internal.misc.Signal, but the
// latter requires granting access. Since the official way to do this is is not
// supported in scala (at least up to 2.13.3) it has to be done e.g. by starting
// the JVM with: --add-exports java.base/jdk.internal.misc=ALL-UNNAMED
// For now simply keep on using the legacy class.

object Signals {

  def setHandler(signal: Signal)(handler: Signal => Unit): SignalHandler = {
    Signal.handle(signal, s => handler(s))
  }

  def setHandler(name: String)(handler: Signal => Unit): SignalHandler = {
    setHandler(new Signal(name))(handler)
  }

  def setSIGHUPHandler(handler: Signal => Unit): SignalHandler = {
    setHandler("HUP")(handler)
  }

}
