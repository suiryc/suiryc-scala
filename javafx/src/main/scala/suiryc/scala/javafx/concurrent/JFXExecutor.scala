package suiryc.scala.javafx.concurrent

import akka.dispatch.{DispatcherPrerequisites, ExecutorServiceConfigurator, ExecutorServiceFactory}
import com.typesafe.config.Config
import java.util.{Collections, List => jList}
import java.util.concurrent.{AbstractExecutorService, ExecutorService, ThreadFactory, TimeUnit}
import javafx.application.Platform
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


// See:
//  - https://groups.google.com/forum/#!msg/scalafx-users/JxXXNTKC4Kk/riJCqyaEG1cJ
//  - https://gist.github.com/saberduck/5150719
//  - https://gist.github.com/viktorklang/2422443

object JFXExecutor {

  /* Execution context based on JavaFX, but not tied to any akka system.
   * May be used when the whole JFXSystem (akka system with dedicated dispatcher
   * and scheduler) is not needed.
   */
  implicit lazy val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutorService(JFXExecutorService)

}

object JFXExecutorService extends AbstractExecutorService {

  def execute(command: Runnable): Unit = Platform.runLater(command)

  def shutdown(): Unit = ()

  def shutdownNow(): jList[Runnable] = Collections.emptyList[Runnable]

  def isShutdown: Boolean = false

  def isTerminated: Boolean = false

  def awaitTermination(l: Long, timeUnit: TimeUnit): Boolean = true

}

class JFXEventThreadExecutorServiceConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
  extends ExecutorServiceConfigurator(config, prerequisites)
{

  private val f = new ExecutorServiceFactory {
    def createExecutorService: ExecutorService = JFXExecutorService
  }

  def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = f

}
