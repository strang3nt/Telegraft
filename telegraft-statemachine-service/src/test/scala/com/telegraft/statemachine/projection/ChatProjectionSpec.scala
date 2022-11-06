package com.telegraft.statemachine.projection

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import akka.projection.testkit.scaladsl.{
  ProjectionTestKit,
  TestProjection,
  TestSourceProvider
}
import akka.stream.scaladsl.Source
import com.telegraft.statemachine.database.{ Chat, Message, UserChat }
import com.telegraft.statemachine.persistence.PersistentChat
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

class ChatProjectionSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike {

  private val projectionTestKit = ProjectionTestKit(system)

  private def createEnvelope(
      event: PersistentChat.Event,
      seqNo: Long,
      timestamp: Long = 0L) =
    EventEnvelope(
      Offset.sequence(seqNo),
      "persistenceId",
      seqNo,
      event,
      timestamp)

  private def toAsyncHandler(itemHandler: ChatProjectionHandler)(
      implicit
      ec: ExecutionContext): Handler[EventEnvelope[PersistentChat.Event]] =
    eventEnvelope =>
      Future {
        itemHandler.process(eventEnvelope)
        Done
      }

  "The events from the PersistentChat" should {

    "should create a new message by the projection" in {

      val newMessage = PersistentChat.Message(
        "userId_0",
        "chatId_0",
        "Message body.",
        Instant.now())

      val events =
        Source(
          List[EventEnvelope[PersistentChat.Event]](
            createEnvelope(PersistentChat.MessageAdded(newMessage), 0L)))

      val repository = TestDatabaseRepository.init
      val projectionId =
        ProjectionId("persistentChatTest", "chats-0")
      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[PersistentChat.Event]](
          events,
          extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[PersistentChat.Event]](
          projectionId,
          sourceProvider,
          () =>
            toAsyncHandler(new ChatProjectionHandler(repository))(
              system.executionContext))

      projectionTestKit.run(projection) {
        repository.messageRepo.messages shouldBe List(
          Message(
            0,
            newMessage.userId,
            newMessage.chatId,
            newMessage.content,
            newMessage.timestamp))
      }
    }
  }
  "should create a new chat with a user inside by the projection" in {
    val newChat = PersistentChat.Chat(
      "chatId_0",
      "chatName",
      "description",
      Set.empty[String],
      List.empty[PersistentChat.Message])
    val chatCreated = PersistentChat.ChatCreated(newChat, "userId_0")

    val events =
      Source(
        List[EventEnvelope[PersistentChat.Event]](
          createEnvelope(chatCreated, 0L)))

    val repository = TestDatabaseRepository.init
    val projectionId =
      ProjectionId("persistentChatTest", "chats-0")
    val sourceProvider =
      TestSourceProvider[Offset, EventEnvelope[PersistentChat.Event]](
        events,
        extractOffset = env => env.offset)
    val projection =
      TestProjection[Offset, EventEnvelope[PersistentChat.Event]](
        projectionId,
        sourceProvider,
        () =>
          toAsyncHandler(new ChatProjectionHandler(repository))(
            system.executionContext))

    projectionTestKit.run(projection) {
      repository.chatRepo.chats shouldBe List(Chat(newChat.id, newChat.name))
      repository.userChatRepo.usersChats shouldBe List(
        UserChat(chatCreated.userId, newChat.id))
    }
  }

  "should let a user join a chat by the projection" in {

    val chatJoined = PersistentChat.ChatJoined("chatId_0", "userId_0")

    val events =
      Source(
        List[EventEnvelope[PersistentChat.Event]](
          createEnvelope(chatJoined, 0L)))

    val repository = TestDatabaseRepository.init
    val projectionId =
      ProjectionId("persistentChatTest", "chats-0")
    val sourceProvider =
      TestSourceProvider[Offset, EventEnvelope[PersistentChat.Event]](
        events,
        extractOffset = env => env.offset)
    val projection =
      TestProjection[Offset, EventEnvelope[PersistentChat.Event]](
        projectionId,
        sourceProvider,
        () =>
          toAsyncHandler(new ChatProjectionHandler(repository))(
            system.executionContext))

    projectionTestKit.run(projection) {
      repository.userChatRepo.usersChats shouldBe List(
        UserChat(chatJoined.userId, chatJoined.chatId))

    }
  }

}
