package suiryc.scala.concurrent

import scala.concurrent.{ExecutionContext, Future}


class CancellableFuture[T](
  cancellable: Cancellable,
  val future: Future[T]
) {

  def cancel(): Unit = cancellable.cancel()

}

object CancellableFuture {

  def apply[T](body: Cancellable => T)(implicit executor: ExecutionContext): CancellableFuture[T] = {
    val cancellable = new Cancellable()
    new CancellableFuture(cancellable, Future(body(cancellable)))
  }

}
