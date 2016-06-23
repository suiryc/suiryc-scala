package suiryc.scala.concurrent

/** ThreadLocal helpers. */
object ThreadLocalEx {

  /** Builds a ThreadLocal with the given initial value supplier. */
  def apply[A](initial: => A): ThreadLocal[A] = new ThreadLocal[A] {
    override def initialValue: A = initial
  }

}
