package com.telegraft.statemachine.persistence

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

object PersistentUserSpec {
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor.serialization-bindings {
        "com.telegraft.statemachine.CborSerializable" = jackson-cbor
      }
      """)
    .withFallback(EventSourcedBehaviorTestKit.config)
}

class PersistentUserSpec
    extends ScalaTestWithActorTestKit(PersistentUserSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  private val userId = "testUser"
  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[
      PersistentUser.Command,
      PersistentUser.Event,
      PersistentUser.User](system, PersistentUser(userId, "users-0"))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "The PersistentUser" should {

    "create a user" in {

      val result =
        eventSourcedTestKit.runCommand[StatusReply[PersistentUser.User]](
          PersistentUser.CreateUser("username", _))

      result.reply should ===(
        StatusReply.Success(PersistentUser.User("testUser", "username")))
      result.event should ===(
        PersistentUser.UserCreated("testUser", "username"))
    }

  }

}
