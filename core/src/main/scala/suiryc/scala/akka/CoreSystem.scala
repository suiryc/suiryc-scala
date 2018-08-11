package suiryc.scala.akka

import akka.actor.ActorSystem
import com.typesafe.config.Config
import monix.execution.Scheduler
import suiryc.scala.Configuration


object CoreSystem {

  /** Configuration ('suiryc-scala' path). */
  val config: Config = Configuration.loaded.getConfig("suiryc-scala")

  /** Core akka system. */
  val system = ActorSystem("suiryc-core", config)
  /** Scheduler running with core system execution context. */
  lazy val scheduler = Scheduler(system.dispatcher)

}
