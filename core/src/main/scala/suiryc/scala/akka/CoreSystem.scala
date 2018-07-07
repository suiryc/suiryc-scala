package suiryc.scala.akka

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import monix.execution.Scheduler


object CoreSystem {

  /** Configuration ('suiryc-scala' path). */
  val config: Config = ConfigFactory.load().getConfig("suiryc-scala")

  /** Core akka system. */
  val system = ActorSystem("suiryc-core", config)
  /** Scheduler running with core system execution context. */
  lazy val scheduler = Scheduler(system.dispatcher)

}
