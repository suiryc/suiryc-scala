package suiryc.scala.akka

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}


object CoreSystem {

  /** Configuration ('suiryc-scala' path). */
  val config: Config = ConfigFactory.load().getConfig("suiryc-scala")

  /** Core akka system. */
  val system = ActorSystem("suiryc-core", config)

}
