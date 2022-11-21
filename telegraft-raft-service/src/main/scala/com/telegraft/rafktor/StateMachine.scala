package com.telegraft.rafktor

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import com.telegraft.rafktor.Log.{
  ChatCreated,
  ChatJoined,
  MessageSent,
  MessagesRetrieved,
  TelegraftRequest,
  TelegraftResponse,
  UserCreated
}
import com.telegraft.statemachine.proto.TelegraftStateMachineService
import scala.util.{ Failure, Success }

/**
 * Actor which receives a payload to deliver to telegraft-statemachine-service
 * and then replies with the answer to whoever asked.
 */
object StateMachine {

  sealed trait Command
  final case class ClientRequest(
      payload: TelegraftRequest,
      replyTo: ActorRef[StatusReply[TelegraftResponse]])
      extends Command
  private final case class ClientResponse(
      payload: StatusReply[TelegraftResponse],
      replyTo: ActorRef[StatusReply[TelegraftResponse]])
      extends Command

  def apply(telegraftService: TelegraftStateMachineService): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case ClientRequest(
              payload,
              replyTo: ActorRef[StatusReply[TelegraftResponse]]) =>
          payload match {
            case r: Log.CreateUser =>
              ctx.pipeToSelf(
                telegraftService.createUser(r.convertToGRPC.value)) {
                case Success(value) =>
                  ClientResponse(
                    StatusReply.Success(value: UserCreated),
                    replyTo)
                case Failure(exception) =>
                  ClientResponse(StatusReply.Error(exception), replyTo)
              }
              Behaviors.same
            case r: Log.SendMessage =>
              ctx.pipeToSelf(
                telegraftService.sendMessage(r.convertToGRPC.value)) {
                case Success(value) =>
                  ClientResponse(
                    StatusReply.Success(value: MessageSent),
                    replyTo)
                case Failure(exception) =>
                  ClientResponse(StatusReply.Error(exception), replyTo)
              }
              Behaviors.same
            case r: Log.CreateChat =>
              ctx.pipeToSelf(
                telegraftService.createChat(r.convertToGRPC.value)) {
                case Success(value) =>
                  ClientResponse(
                    StatusReply.Success(value: ChatCreated),
                    replyTo)
                case Failure(exception) =>
                  ClientResponse(StatusReply.Error(exception), replyTo)
              }
              Behaviors.same
            case r: Log.JoinChat =>
              ctx.pipeToSelf(telegraftService.joinChat(r.convertToGRPC.value)) {
                case Success(value) =>
                  ClientResponse(
                    StatusReply.Success(value: ChatJoined),
                    replyTo)
                case Failure(exception) =>
                  ClientResponse(StatusReply.Error(exception), replyTo)
              }
              Behaviors.same
            case r: Log.GetMessages =>
              ctx.pipeToSelf(
                telegraftService.getMessages(r.convertToGRPC.value)) {
                case Success(value) =>
                  ClientResponse(
                    StatusReply.Success(value: MessagesRetrieved),
                    replyTo)
                case Failure(exception) =>
                  ClientResponse(StatusReply.Error(exception), replyTo)
              }
              Behaviors.same
          }
        case ClientResponse(payload, replyTo) =>
          replyTo ! payload
          Behaviors.same
      }
    }

}
