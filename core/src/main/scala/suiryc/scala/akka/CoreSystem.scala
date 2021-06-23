package suiryc.scala.akka

import akka.actor.Terminated

import scala.concurrent.Future

object CoreSystem {

  // Standard akka resources for non-blocking asynchronous code.
  // Must not be used by blocking code.
  object NonBlocking extends AkkaResources("non-blocking")

  // Akka resources for blocking code.
  //
  // Notes:
  // Must be used by actors or asynchronous transformations (e.g. Future.map)
  // that do call blocking code. e.g. backs 'RichFuture.blockingAsync' which
  // wraps blocking code inside a Future to make its execution asynchronous
  // and usable by non-blocking caller.
  // There is a limit on threads available to execute code, and thus on code
  // being executed in parallel.
  //
  // Even though sharing the same system as non-blocking code is feasible by
  // only using a dedicated dispatcher for code execution, the system scheduler
  // is also shared and may, in some cases, end up executing scheduled blocking
  // code.
  // It is thus preferable to create a dedicated system too, hence separating
  // all resources.
  object Blocking extends AkkaResources("blocking")

  /** Terminates non-blocking then blocking systems. */
  def terminate(): Future[Terminated] = {
    NonBlocking.system.terminate().flatMap { _ =>
      Blocking.system.terminate()
    }(Blocking.dispatcher)
  }

}
