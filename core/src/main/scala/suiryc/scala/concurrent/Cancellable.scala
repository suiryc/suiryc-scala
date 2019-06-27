package suiryc.scala.concurrent

// Note:
// We purposely have 'Cancelled' be a class instead of an object so that we have
// distincts stacktraces when used.
case class Cancelled() extends RuntimeException("Cancelled")

/** Simple cancellable resource. */
class Cancellable {

  private var cancelled = false

  /** Gets whether this Cancellable has been cancelled. */
  def isCancelled: Boolean = cancelled

  /** Cancels. */
  def cancel(): Unit = {
    cancelled = true
  }

  /**
   * Checks whether we have been cancelled.
   *
   * If we were not cancelled, nothing is done.
   * Otherwise the optional cleanup code is executed and Cancelled is thrown.
   *
   * @param cleanup code to execute if we had been cancelled
   * @throws Cancelled if we had been cancelled
   */
  def check(cleanup: => Unit = {}): Unit = {
    if (cancelled) {
      cleanup
      throw Cancelled()
    }
  }

}

object Cancellable {

  /** Builds a Cancellable wrapping multiple Cancellables. */
  def apply(cancellables: Seq[Cancellable]): Cancellable = {
    new Cancellable {
      override def cancel(): Unit = {
        cancellables.foreach(_.cancel())
        super.cancel()
      }
    }
  }

  /**
   * Builds a Cancellable wrapping multiple Cancellables.
   *
   * vararg variant.
   */
  def apply(cancellables: Cancellable*)(implicit d: DummyImplicit): Cancellable = Cancellable(cancellables.toSeq)

}
