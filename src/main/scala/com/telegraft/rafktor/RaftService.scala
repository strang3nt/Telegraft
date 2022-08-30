package com.telegraft.rafktor

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import akka.actor.typed.ActorSystem
import akka.persistence.typed.PersistenceId
import akka.cluster.typed.Cluster

/**
  * RaftService handles subscription to receptionist, changes in 
  * cluster memberships and starts raft node.
  */
object RaftService {
  
  sealed trait Command
  final case class MsgFromClient(c: SMCommand, replyTo: ActorRef[StatusReply[SMResponse]]) extends Command
  final case class NewConfig(listing: Receptionist.Listing) extends Command

  def apply(stateMachine: ActorRef[SMCommand])(implicit system: ActorSystem[_]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      
      val clustering = Cluster(system)

      // registration at startup
      val raftNode = context.spawnAnonymous(RaftNode(stateMachine, PersistenceId.ofUniqueId("RaftNode")))

      val listingResponseAdapter = context.messageAdapter[Receptionist.Listing](NewConfig.apply)
      context.system.receptionist ! Receptionist.Subscribe(RaftNode.RaftServiceKey, listingResponseAdapter)

      Behaviors.receiveMessage[Command] {
        case MsgFromClient(smcommand, replyTo) =>
          // send to raft node`
          Behaviors.same
        case NewConfig(listing) =>
          Behaviors.same
      }

    }
  // TODO: send append entry to add to log config change
}
