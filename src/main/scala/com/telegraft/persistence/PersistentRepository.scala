package com.telegraft.persistence

import java.time.LocalDateTime
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import com.telegraft.rafktor.CborSerializable

import scala.concurrent.duration.DurationInt

object PersistentRepository {

  def tags: Set[String] = Set("user-add")

  case class User(username: String)
  case class Message(sender: String, content: String, timestamp: LocalDateTime)

  sealed trait Command extends CborSerializable
  case class CreateChat(name: String, creator: String, description: String, replyTo: ActorRef[StatusReply[String]]) extends Command
  case class SendMessageTo(sender: String, receiver: Int, content: String, timestamp: LocalDateTime, replyTo: ActorRef[StatusReply[String]]) extends Command
  case class JoinChat(chatId: Int, user: String, replyTo: ActorRef[StatusReply[String]]) extends Command
  case class CreateUser(username: String, replyTo: ActorRef[StatusReply[User]]) extends Command

  case class State(chats: Map[Int, ActorRef[PersistentChat.Command]], users: Vector[User]) extends CborSerializable

  sealed trait Event extends CborSerializable
  case class ChatCreated(chatId: Int) extends Event
  case class UserCreated(user: User) extends Event

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>

      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId("TelegraftRepository"),
        emptyState = State(Map.empty[Int, ActorRef[PersistentChat.Command]], Vector.empty[User]),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context))
      .withTagger(_ => tags)
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
    }

  def commandHandler(context: ActorContext[Command]):
  (State, Command) => Effect[Event, State] = { (state, command) =>

    command match {

      case CreateUser(username, replyTo) =>
        state.users.find(_.username == username) match {
          case Some (user) => Effect.reply (replyTo) (StatusReply.error (s"Error: user '${user.username}' already exists.") )
          case None =>
            val user = User (username)
            Effect
              .persist (UserCreated (user) )
              .thenReply (replyTo) (_ => StatusReply.Success (user) )
        }

      case CreateChat(name, creator, description, replyTo) =>
        val id = state.chats.keys.max + 1
        val chat = context.spawn(PersistentChat(id), "Chat#" + id)
        Effect
          .persist(ChatCreated(id))
          .thenReply(chat)(_ => PersistentChat.CreateChat(name, creator, description, replyTo))

      case SendMessageTo(creator, receiver, content, timestamp, replyTo) =>
        state.chats.get(receiver) match {
          case Some(chat) => Effect.reply(chat)(PersistentChat.SendMessageTo(creator, receiver, content, timestamp, replyTo))
          case None => Effect.reply(replyTo)(StatusReply.Error(s"Chat: $receiver doesn't exist."))
        }

      case JoinChat(chatId, user, replyTo) =>
        state.chats.get(chatId) match {
          case Some(chat) => Effect.reply(chat)(PersistentChat.JoinChat(chatId, user, replyTo))
          case None => Effect.reply(replyTo)(StatusReply.Error(s"Chat: $chatId doesn't exist."))
        }

    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) =>
    event match {
      case UserCreated(user) => state.copy(users = state.users :+ user)
      case ChatCreated(id) =>
        val chat = context.child("Chat#" + id) // exists after command handler,
          .getOrElse(context.spawn(PersistentChat(id), "Chat#" + id)) // does NOT exist in the recovery mode, so needs to be created
          .asInstanceOf[ActorRef[PersistentChat.Command]]
        state.copy(chats = state.chats + (id -> chat))
    }

}
