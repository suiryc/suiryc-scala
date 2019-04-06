package suiryc.scala.util

import scala.util.{Failure, Try}

/**
 * AutoCloseable management.
 *
 * Inspired by scala 2.13 features (not yet released), reduced to the bare
 * minimum.
 */
object Using {

  /**
   * Closes resource.
   *
   * Keep original result when possible.
   * If closing fails, propagate the issue unless original result was a failure.
   */
  private def close[A](resource: AutoCloseable, result: Try[A]): Try[A] = {
    try {
      resource.close()
      result
    } catch {
      case ex: Throwable ⇒
        if (result.isFailure) result
        else Failure(ex)
    }
  }

  /**
   * Manages a resource.
   *
   * Executes code and closes resource before returning result.
   * If code or closing fails, the issue is returned.
   */
  def apply[A <: AutoCloseable, B](resource: ⇒ A)(f: A ⇒ B): Try[B] = {
    val actualResource = Try(resource)
    val r = actualResource.map(f)
    actualResource.map(close(_, r)).getOrElse(r)
  }

  protected class Manager {

    private var managed = List.empty[AutoCloseable]

    /** Registers a new resources to manage. */
    def apply[A <: AutoCloseable](resource: A): A = {
      managed ::= resource
      resource
    }

    /** Closes resources in reverse order compared to opening. */
    protected def cleanup[A](result: Try[A]): Try[A] = {
      managed.foldLeft(result) { (result, resource) ⇒
        close(resource, result)
      }
    }

  }

  object Manager {

    /**
     * Manages multiple resources.
     *
     * Resources are to be registered through the given manager. Closing is done
     * in reverse order compared to registering.
     * If code or closing fails, the issue is returned. All resources are closed
     * even upon issue.
     */
    def apply[A](f: Manager ⇒ A): Try[A] = {
      val manager = new Manager
      val r = Try(f(manager))
      manager.cleanup(r)
    }

  }

}
