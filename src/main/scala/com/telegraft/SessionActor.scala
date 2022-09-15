package com.telegraft

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.telegraft.rafktor.{RaftService, SMCommand, SMResponse}

object SessionActor {

  def apply(forwardTo: ActorRef[RaftService.Command]): Behavior[Command] =
    Behaviors.setup[Command] { context =>

      val responseAdapter: ActorRef[SMResponse] =
        context.messageAdapter[SMResponse](x => ResponseWrapper(x))

      def sendCommand(forwardTo: ActorRef[RaftService.Command]): Behavior[Command] =
        Behaviors.receiveMessagePartial[Command] {
          case MsgForRaftService(command, replyTo) =>
            forwardTo ! RaftService.MsgFromClient(command, responseAdapter)
            expectReply(forwardTo, replyTo)
        }

      def expectReply(forwardTo: ActorRef[RaftService.Command], replyTo: ActorRef[SMResponse]): Behavior[Command] =
        Behaviors.receiveMessagePartial[Command] {
          case ResponseWrapper(response) =>
            replyTo ! response
            sendCommand(forwardTo)
        }

      sendCommand(forwardTo)
    }

  sealed trait Command
  final case class MsgForRaftService(command: SMCommand, replyTo: ActorRef[SMResponse]) extends Command
  final case class ResponseWrapper(reply: SMResponse) extends Command

}
