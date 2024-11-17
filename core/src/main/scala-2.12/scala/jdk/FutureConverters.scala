package scala.jdk

import java.util.concurrent.CompletionStage
import scala.compat.java8.{FutureConverters => cj8FutureConverters}
import scala.concurrent.Future

/**
 * Converters between Java CompletionStage and Scala Future.
 *
 * Exposed the same way scala 2.13 does, using scala-java8-compat for actual
 * implementation.
 */
object FutureConverters {

  implicit class FutureOps[T](private val f: Future[T]) extends AnyVal {
    /** Convert a Scala Future to a Java CompletionStage. */
    def asJava: CompletionStage[T] = cj8FutureConverters.toJava(f)
  }

  implicit class CompletionStageOps[T](private val cs: CompletionStage[T]) extends AnyVal {
    /** Convert a Java CompletionStage to a Scala Future. */
    def asScala: Future[T] = cj8FutureConverters.toScala(cs)
  }

}
