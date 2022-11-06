package com.telegraft.statemachine.persistence

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.Instant

object PersistentChatSpec {
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor.serialization-bindings {
        "com.telegraft.statemachine.CborSerializable" = jackson-cbor
      }
      """)
    .withFallback(EventSourcedBehaviorTestKit.config)
}
class PersistentChatSpec
    extends ScalaTestWithActorTestKit(PersistentChatSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  private val chatId = "testChat"
  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[
      PersistentChat.Command,
      PersistentChat.Event,
      PersistentChat.Chat](system, PersistentChat(chatId, "chats-0"))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "The PersistentChat" should {

    "create a chat" in {

      val result =
        eventSourcedTestKit.runCommand[StatusReply[PersistentChat.Chat]](
          PersistentChat.CreateChat("name", "creator", "description", _))
      result.reply should ===(
        StatusReply.Success(
          PersistentChat.Chat(
            "testChat",
            "name",
            "description",
            Set("creator"),
            List.empty[PersistentChat.Message])))

      result.event should ===(
        PersistentChat.ChatCreated(
          PersistentChat.Chat(
            "testChat",
            "name",
            "description",
            Set.empty[String],
            List.empty[PersistentChat.Message]),
          "creator"))

    }

    "should allow a member to join it" in {
      val result =
        eventSourcedTestKit.runCommand[StatusReply[Done]](
          PersistentChat.JoinChat("testChat", "userId", _))

      result.reply should ===(StatusReply.ack())
      result.event should ===(PersistentChat.ChatJoined("testChat", "userId"))
    }

    "" in {

      val timestamp = Instant.now()

      val result =
        eventSourcedTestKit.runCommand[StatusReply[PersistentChat.Message]](
          PersistentChat
            .SendMessageTo("userId", "testChat", "content", timestamp, _))

      result.reply should ===(
        StatusReply.Success(
          PersistentChat.Message("userId", "testChat", "content", timestamp)))
      result.event should ===(
        PersistentChat.MessageAdded(
          PersistentChat.Message("userId", "testChat", "content", timestamp)))
    }
  }
}
