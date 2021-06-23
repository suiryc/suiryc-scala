package suiryc.scala.akka

import akka.actor.Deploy.NoDispatcherGiven
import akka.actor.{ActorContext, ActorRef, ActorSystem, Props}
import com.typesafe.config.Config
import monix.execution.Scheduler
import suiryc.scala.Configuration

import scala.concurrent.ExecutionContextExecutor

object AkkaResources {

  // Notes:
  // Even when using a dedicated system (for blocking code), we really need to
  // declare and use a dedicated dispatcher: while configuring the default one
  // seems easier (e.g. no need to explicitly use it when creating new actors)
  // it has unwanted side effects (fork join executor thread pool appears to
  // never shrink in this case ...).
  // Code needing it (Future or actor handling, ...) thus has to explicitly
  // use this dedicated dispatcher instead of the default system one.
  //
  // For easier handling we:
  //  - create a dummy configuration with the wanted dispatcher at a fixed path
  //  - lookup the dispatcher at this fixed path if present, or use the default
  //  - have an helper to create actors with the dedicated dispatcher added

  // "thread-pool-executor" has the following behaviour:
  // It is created with a "blocking queue", but the executor actually calls
  // "offer" and not "put":
  //   - "put" blocks caller when queue is full
  //   - "offer" returns false, and executor hands runnable to rejection handler
  // The akka rejection handler throws an error if system is shutting down, and
  // otherwise executes itself the runnable - and thus blocks an unknown amount
  // of time the caller thread, which can be the system "scheduler" when
  // scheduling code execution, or may be the system "default dispatcher" when
  // executing actor code.
  // Due to the fact akka uses batching execution, mixing with non-blocking
  // scheduling may also end up blocking the non-blocking scheduler.
  // In many situations, it thus prevents the caller thread from doing its usual
  // job in a timely manner until the executed code returns, which could mean
  // available threads may wait for code to be executed being dispatched.
  // The default configuration uses an unbounded queue (task-queue-size=-1).

  // Fixed path where dedicated dispatcher is configured in dummy Config.
  private val GENERATED_DISPATCHER_ID = s"${Configuration.KEY_LIB}.generated.akka.dispatcher"

  // Application configuration path for dispatcher dedicated to a given usage
  // (blocking/non-blocking).
  private def getDispatcherId(name: String): String = s"${Configuration.KEY_LIB}.dispatcher.$name"

  // Creates dummy configuration with dedicated dispatcher, if applicable, at
  // a fixed path.
  private def getConfig(name: String): Config = {
    import suiryc.scala.ConfigTools._

    val config = Configuration.libConfig.withOnlyPath("akka").withFallback(Configuration.loaded)
    config
      .getOptionalConfig(getDispatcherId(name))
      .map { dispatcherConfig =>
        dispatcherConfig.atPath(GENERATED_DISPATCHER_ID).withFallback(config)
      }
      .getOrElse(config)
  }

  // Uses the dedicated dispatcher if present.
  // We only need to check whether the system knows the dispatcher since it is
  // looked up (and cached) upon creating the system.
  private def wrapProps(system: ActorSystem, props: Props): Props = {
    if ((props.deploy.dispatcher == NoDispatcherGiven) && system.dispatchers.hasDispatcher(GENERATED_DISPATCHER_ID)) {
      props.withDispatcher(GENERATED_DISPATCHER_ID)
    } else {
      props
    }
  }

  /** Creates an actor with dedicated dispatcher if applicable. */
  def actorOf(context: ActorContext, props: Props): ActorRef = {
    context.actorOf(wrapProps(context.system, props))
  }

  def actorOf(context: ActorContext, props: Props, name: String): ActorRef = {
    context.actorOf(wrapProps(context.system, props), name)
  }

}

class AkkaResources(name: String) {

  import AkkaResources._

  private val config: Config = getConfig(name)

  implicit val system: ActorSystem = ActorSystem(s"${Configuration.KEY_LIB}-$name", config)
  implicit val dispatcher: ExecutionContextExecutor = if (config.hasPath(GENERATED_DISPATCHER_ID)) {
    system.dispatchers.lookup(GENERATED_DISPATCHER_ID)
  } else {
    system.dispatcher
  }

  /** Scheduler running with system execution context. */
  lazy val scheduler: Scheduler = Scheduler(dispatcher)

  /** Creates an actor with dedicated dispatcher if applicable. */
  def actorOf(props: Props): ActorRef = {
    system.actorOf(wrapProps(system, props))
  }

  def actorOf(props: Props, name: String): ActorRef = {
    system.actorOf(wrapProps(system, props), name)
  }

}
