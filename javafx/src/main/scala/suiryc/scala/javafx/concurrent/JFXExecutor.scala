package suiryc.scala.javafx.concurrent

import java.util.Collections
import java.util.concurrent.{AbstractExecutorService, TimeUnit}
import scala.concurrent.{ExecutionContextExecutor, ExecutionContext}
import scalafx.application.Platform


/* See:
 *  - https://groups.google.com/forum/#!msg/scalafx-users/JxXXNTKC4Kk/riJCqyaEG1cJ
 *  - https://gist.github.com/saberduck/5150719
 *  - https://gist.github.com/viktorklang/2422443
 */

object JFXExecutor {

  implicit lazy val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutorService(JFXExecutorService)

}

object JFXExecutorService extends AbstractExecutorService {

  def execute(command: Runnable) = Platform.runLater(command)

  def shutdown(): Unit = ()

  def shutdownNow() = Collections.emptyList[Runnable]

  def isShutdown = false

  def isTerminated = false

  def awaitTermination(l: Long, timeUnit: TimeUnit) = true

}
