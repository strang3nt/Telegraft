package com.telegraft.persistence

import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import java.time.LocalDateTime
import scala.collection.immutable.{List, Vector}

private[persistence] object PersistentChat {

  case class Message(sender: String, content: String, timestamp: LocalDateTime)

  sealed trait Command
  case class CreateChat(name: String, creator: String, description: String, replyTo: ActorRef[StatusReply[String]]) extends Command
  case class SendMessageTo(sender: String, receiver: Int, content: String, timestamp: LocalDateTime, replyTo: ActorRef[StatusReply[String]]) extends Command
  case class JoinChat(chatId: Int, userId: String, replyTo: ActorRef[StatusReply[String]]) extends Command
  
  // state
  case class Chat(
    id: Int,
    name: String,
    description: String,
    members: Vector[String],
    messages: List[Message]
  )

  sealed trait Event
  object Event {
    case class MessageAdded(msg: Message) extends Event

    case class ChatCreated(c: Chat) extends Event

    case class ChatJoined(userId: String) extends Event
  }

  def apply(id: Int): Behavior[Command] =
    EventSourcedBehavior[Command, Event, Chat](
      persistenceId = PersistenceId.ofUniqueId("Chat#" + id),
      emptyState = Chat(id, "", "", Vector.empty[String], List.empty[Message]),
      commandHandler = commandHandler,
      eventHandler = eventHandler)

  val commandHandler: (Chat, Command) => Effect[Event, Chat] = { (state, command) =>
    import Event._

    command match {
      case CreateChat(name, creator, description, replyTo) =>
        Effect
          .persist(ChatCreated(Chat(state.id, name, description, state.members :+ creator, state.messages)))
          .thenReply(replyTo)(_ => StatusReply.Success(s"Chat created with id ${state.id}"))

      case SendMessageTo(sender, receiver, content, timestamp, replyTo) =>
        val msg = Message(sender, content, timestamp)
        if (receiver == state.id)
          Effect
            .persist(MessageAdded(msg))
            .thenReply(replyTo)(_ => StatusReply.Success(s"Message sent from user '$sender' to chat '${state.id}'"))
        else Effect.reply(replyTo)(StatusReply.Error(new RuntimeException(s"Message for chat '$receiver' sent to chat '${state.id}'.")))

      case JoinChat(chatId, userId, replyTo) =>
        if (chatId == state.id)
          Effect
            .persist(ChatJoined(userId))
            .thenReply(replyTo)(_ => StatusReply.Success(s"User '$userId' joined chat '${state.id}'"))
        else Effect.reply(replyTo)(StatusReply.Error(new RuntimeException(s"Request to join '$chatId', but request sent to chat '${state.id}'.")))
    }
  }
  val eventHandler: (Chat, Event) => Chat = { (chat, event) =>
    import Event._

    event match {
      case MessageAdded (msg) => chat.copy(messages = chat.messages :+ msg)
      case ChatCreated (c) => c
      case ChatJoined (userId) => chat.copy(members = chat.members :+ userId)
    }
  }

}
