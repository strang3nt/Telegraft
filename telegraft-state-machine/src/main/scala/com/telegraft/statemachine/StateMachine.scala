package com.telegraft.statemachine

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.telegraft.statemachine.persistence._

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}
import java.time.LocalDateTime

object StateMachine {

  final case class Chat(userId: String, name: String, description: String)
  final case class Message(userId: String, chatId: Int, content: String)
  final case class User(username: String)

  sealed trait Command
  final case class CreateChat(userId: Long, c: Chat, replyTo: ActorRef[Response]) extends Command
  final case class GetUser(userId: Long, replyTo: ActorRef[Response]) extends Command
  final case class CreateUser(userName: String, replyTo: ActorRef[Response]) extends Command
  final case class SendMessageTo(userId: Long, receiverId: Long, content: String, replyTo: ActorRef[Response]) extends Command
  final case class JoinChat(userId: Long, chatId: Long, replyTo: ActorRef[Response]) extends Command
  final case class GetUserMessages(userId: Long, timestamp: LocalDateTime, replyTo: ActorRef[Response]) extends Command
  
  private final case class WrappedResponse(any: Response, replyTo: ActorRef[Response]) extends Command

  sealed trait Response
  final case class ActionPerformed(msg: String) extends Response
  final case class ActionFailed(err: String) extends Response
  final case class Messages(userId: Long, messages: Seq[Message]) extends Response
  final case class GetUserResponse(maybeUser: Option[User]) extends Response

  def apply(persistentRepository: ActorRef[PersistentRepository.Command]): Behavior[Command] = Behaviors.receivePartial[Command] {
    case (ctx, command) => {

      implicit val timeout: Timeout = 3.seconds
      command match {
        case CreateChat(userId, c, replyTo) => Behaviors.same
        case GetUser(userId, replyTo) => Behaviors.same
        case CreateUser(username, replyTo) =>
          ctx.askWithStatus(persistentRepository, PersistentRepository.CreateUser(username, _)) {
            case Success(_) => WrappedResponse(ActionPerformed("User " + username + " created"), replyTo)
            case Failure(e) => WrappedResponse(ActionFailed(e.getMessage), replyTo)
          }
          Behaviors.same
        case SendMessageTo(userId, receiverId, content, replyTo) => Behaviors.same
        case JoinChat(userId, chatId, replyTo) => Behaviors.same
        case GetUserMessages(userId, timestamp, replyTo) => Behaviors.same
        case WrappedResponse(result, replyTo) =>
          replyTo ! result
          Behaviors.same
      }
    }
  }


}
