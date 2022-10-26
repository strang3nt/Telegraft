package com.telegraft.statemachine.persistence

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

import scala.concurrent.duration.DurationInt

object PersistentUser {

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("PersistentUser")

  val tags: Seq[String] = Vector.tabulate(5)(i => s"persistentUsers-$i")

  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[Command] => Behavior[Command] = {
      entityContext =>
        val i = math.abs(entityContext.entityId.hashCode % tags.size)
        val selectedTag = tags(i)
        PersistentUser(entityContext.entityId, selectedTag)
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }

  final case class User(userId: String, userName: String)

  sealed trait Command extends CborSerializable

  final case class CreateUser(
      username: String,
      replyTo: ActorRef[StatusReply[User]])
      extends Command

  sealed trait Event extends CborSerializable
  final case class UserCreated(userId: String, userName: String) extends Event

  private def apply(
      persistentUserId: String,
      projectionTag: String): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, User](
        persistenceId = PersistenceId(EntityKey.name, persistentUserId),
        emptyState = User(persistentUserId, ""),
        commandHandler =
          (state, command) => handleCommand(persistentUserId, state, command),
        eventHandler = (state, event) => handleEvent(state, event))
      .withTagger(_ => Set(projectionTag))
      .withRetention(RetentionCriteria
        .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }

  private def handleCommand(
      persistentUserId: String,
      state: User,
      command: Command): ReplyEffect[Event, User] =
    command match {
      case CreateUser(username, replyTo) =>
        Effect
          .persist(UserCreated(persistentUserId, username))
          .thenReply(replyTo)(StatusReply.Success(_))
    }

  private def handleEvent(state: User, event: Event): User =
    event match {
      case UserCreated(userId, userName) =>
        state.copy(userId = userId, userName = userName)
    }

}
