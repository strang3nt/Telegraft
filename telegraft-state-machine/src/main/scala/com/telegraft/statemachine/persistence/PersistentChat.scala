package com.telegraft.statemachine.persistence

import akka.Done
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, SupervisorStrategy }
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  Entity,
  EntityContext,
  EntityTypeKey
}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect,
  RetentionCriteria
}
import com.telegraft.statemachine.CborSerializable

import java.time.Instant
import scala.collection.immutable.{ List, Vector }
import scala.concurrent.duration.DurationInt

object PersistentChat {

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("PersistentChat")

  val tags: Seq[String] = Vector.tabulate(5)(i => s"persistentChats-$i")

  def init(system: ActorSystem[_]): ActorRef[ShardingEnvelope[Command]] = {
    val behaviorFactory: EntityContext[Command] => Behavior[Command] = {
      entityContext =>
        val i = math.abs(entityContext.entityId.hashCode % tags.size)
        val selectedTag = tags(i)
        PersistentChat(entityContext.entityId, selectedTag)
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }
  final case class Message(
      userId: String,
      chatId: String,
      content: String,
      timestamp: Instant)
      extends CborSerializable

  sealed trait Command extends CborSerializable
  final case class CreateChat(
      name: String,
      creator: String,
      description: String,
      replyTo: ActorRef[StatusReply[Chat]])
      extends Command
  final case class SendMessageTo(
      sender: String,
      receiver: String,
      content: String,
      timestamp: Instant,
      replyTo: ActorRef[StatusReply[Message]])
      extends Command
  final case class JoinChat(
      chatId: String,
      userId: String,
      replyTo: ActorRef[StatusReply[Done]])
      extends Command

  // state
  final case class Chat(
      id: String,
      name: String,
      description: String,
      members: Set[String],
      messages: List[Message])
      extends CborSerializable {
    def setId(newId: String): Chat = copy(id = newId)
    def addMessage(msg: Message): Chat = copy(messages = this.messages :+ msg)
    def addUser(userId: String): Chat = copy(members = this.members + userId)
  }
  object Chat {
    val empty: Chat = Chat("", "", "", Set.empty[String], List.empty)
  }
  sealed trait Event extends CborSerializable
  final case class MessageAdded(msg: Message) extends Event
  final case class ChatCreated(c: Chat, userId: String) extends Event
  final case class ChatJoined(chatId: String, userId: String) extends Event

  private def apply(
      persistentChatId: String,
      projectionTag: String): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, Chat](
        persistenceId = PersistenceId(EntityKey.name, persistentChatId),
        emptyState = Chat.empty.setId(persistentChatId),
        commandHandler =
          (state, command) => handleCommand(persistentChatId, state, command),
        eventHandler = (state, event) => handleEvent(state, event))
      .withTagger(_ => Set(projectionTag))
      .withRetention(RetentionCriteria
        .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }

  private def handleCommand(
      chatId: String,
      state: Chat,
      command: Command): ReplyEffect[Event, Chat] = {
    command match {
      case CreateChat(name, creator, description, replyTo) =>
        Effect
          .persist(
            ChatCreated(
              Chat(chatId, name, description, state.members, state.messages),
              creator))
          .thenReply(replyTo)(StatusReply.Success(_))

      case SendMessageTo(sender, receiver, content, timestamp, replyTo) =>
        val msg = Message(sender, chatId, content, timestamp)
        if (receiver == state.id)
          Effect
            .persist(MessageAdded(msg))
            .thenReply(replyTo)(_ => StatusReply.Success(msg))
        else
          Effect.reply(replyTo)(
            StatusReply.Error(new RuntimeException(
              s"Message for chat '$receiver' sent to chat '${state.id}'.")))

      case JoinChat(chatId, userId, replyTo) =>
        if (chatId == state.id)
          Effect
            .persist(ChatJoined(chatId, userId))
            .thenReply(replyTo)(_ => StatusReply.ack())
        else
          Effect.reply(replyTo)(StatusReply.Error(new RuntimeException(
            s"Request to join '$chatId', but request sent to chat '${state.id}'.")))
    }
  }
  private def handleEvent(chat: Chat, event: Event): Chat = {
    event match {
      case MessageAdded(msg)      => chat.addMessage(msg)
      case ChatCreated(c, userId) => c.addUser(userId)
      case ChatJoined(_, userId)  => chat.addUser(userId)
    }
  }

}
