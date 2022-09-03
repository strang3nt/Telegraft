package com.telegraft

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.telegraft.rafktor.{RaftService, SMCommand, SMResponse}

object SessionActor {

  def apply(forwardTo: ActorRef[RaftService.Command]): Behavior[Command] =
    Behaviors.setup[Command] { context =>

      val smResponseAdapter: ActorRef[SMResponse] =
        context.messageAdapter[SMResponse](x => SMResponseWrapper(x))

      sendCommand(forwardTo, smResponseAdapter)

    }

  def sendCommand(forwardTo: ActorRef[RaftService.Command], msgAdapter: ActorRef[SMResponse]): Behavior[Command] =
    Behaviors.receiveMessagePartial[Command] {
      case MsgForRaftService(command, replyTo) =>
        forwardTo ! RaftService.MsgFromClient(command, msgAdapter)
        expectReply(replyTo)
    }

  def expectReply(replyTo: ActorRef[SMResponse]): Behavior[Command] =
    Behaviors.receiveMessagePartial[Command] {
      case SMResponseWrapper(response) =>
        replyTo ! response
        Behaviors.stopped
    }

  sealed trait Command

  final case class MsgForRaftService(command: SMCommand, replyTo: ActorRef[SMResponse]) extends Command

  final case class SMResponseWrapper(reply: SMResponse) extends Command

}
