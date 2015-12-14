package suiryc.scala.concurrent


case class Cancelled() extends RuntimeException("Cancelled")

class Cancellable {

  private var _cancelled = false

  def cancelled(): Boolean = _cancelled

  def cancel() {
    _cancelled = true
  }

  def check(cleanup: => Unit = {}) {
    if (_cancelled) {
      cleanup
      throw Cancelled()
    }
  }

}
