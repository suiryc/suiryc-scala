package suiryc.scala.akka

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory


object CoreSystem {

  val config = ConfigFactory.load().getConfig("suiryc-scala")

  val system = ActorSystem("suiryc-core", config)

}
