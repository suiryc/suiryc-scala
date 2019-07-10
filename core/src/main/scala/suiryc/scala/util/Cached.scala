package suiryc.scala.util

/** Computed value caching. */
class Cached[A](compute: => A) extends (() => A) {

  // Cached value (if already computed)
  private var cached = Option.empty[A]

  /** Underlying cached value. */
  def option: Option[A] = cached

  /** Gets the value (and caches it). */
  def value: A = this.synchronized {
    cached.getOrElse {
      val v = compute
      cached = Some(v)
      v
    }
  }

  override def apply(): A = value

  /** Invalidates the cached value. */
  def invalidate(): Unit = this.synchronized {
    cached = None
  }

  /** Re-computes value: invalidates and gets value. */
  def recompute(): A = {
    invalidate()
    value
  }

}

object Cached {

  def apply[A](compute: => A): Cached[A] = new Cached(compute)

}
