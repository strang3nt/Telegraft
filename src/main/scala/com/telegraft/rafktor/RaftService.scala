package com.telegraft.rafktor

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.cluster.typed.Cluster
import akka.persistence.typed.PersistenceId
import akka.util.Timeout

/**
 * RaftService handles subscription to receptionist, changes in
 * cluster memberships and starts raft node.
 */
object RaftService {
  def apply(stateMachine: ActorRef[SMCommand])(implicit system: ActorSystem[_]): Behavior[Command] =
    Behaviors.setup[Command] { context =>

      implicit val scheduler: Scheduler = system.scheduler
      implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("akka.routes.ask-timeout"))
      val clustering = Cluster(system)

      // registration at startup
      val raftNode = context.spawnAnonymous(RaftNode.apply(stateMachine, PersistenceId.ofUniqueId("RaftNode")))

      val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](NewConfig.apply)

      context.system.receptionist ! Receptionist.Subscribe(RaftNode.RaftServiceKey, listingResponseAdapter)

      Behaviors.receiveMessage[Command] {
        case MsgFromClient(command, replyTo) =>
          raftNode ! RaftNode.MsgFromClient(command, replyTo)
          // send to raft node`
          Behaviors.same
        case NewConfig(listing)
        =>
          Behaviors.same
      }
      // TODO: send append entry to add to log config change
    }

  sealed trait Command

  final case class MsgFromClient(c: SMCommand, replyTo: ActorRef[SMResponse]) extends Command

  final case class NewConfig(listing: Receptionist.Listing) extends Command
}
