package com.telegraft.rafktor

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.telegraft.SMProtocol


/**
 * RaftService handles subscription to receptionist, changes in
 * cluster memberships and starts raft node.
 */
object RaftService {
  def apply(stateMachine: ActorRef[SMProtocol.Command]): Behavior[Command] =
    Behaviors.setup[Command] { context =>

      implicit val scheduler: Scheduler = context.system.scheduler
      implicit val timeout: Timeout = Timeout.create(context.system.settings.config.getDuration("akka.routes.ask-timeout"))

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
