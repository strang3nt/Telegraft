package com.telegraft.rafktor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit

class ConfigurationSpec extends ScalaTestWithActorTestKit with org.scalatest.wordspec.AnyWordSpecLike {

  "Configuration instance" should {
    "load a configuration correctly" in {
      val config = Configuration.apply(system)
      val servers = config.getConfiguration

      val ids = servers.map(_.id)

      ids shouldBe Set("0.0.0.0" + ":" + "8351", "0.0.0.0" + ":" + "8352")

    }
  }
}
