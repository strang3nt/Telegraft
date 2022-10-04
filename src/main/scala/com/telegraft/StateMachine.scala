package com.telegraft

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.telegraft.persistence._
import com.telegraft.rafktor.{SMCommand, SMProtocol, SMResponse}

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object StateMachine extends SMProtocol {

  import SMProtocol._

  final case class Chat(userId: String, name: String, description: String)
  final case class Message(userId: String, chatId: Int, content: String)
  final case class User(username: String)

  final case class CreateChat(userId: Long, c: Chat) extends SMCommand
  final case class GetUser(userId: Long) extends SMCommand
  final case class CreateUser(userName: String) extends SMCommand
  final case class SendMessageTo(userId: Long, receiverId: Long, content: String) extends SMCommand
  final case class JoinChat(userId: Long, chatId: Long) extends SMCommand
  final case class GetUserMessages(userId: Long, timestamp: java.time.Instant) extends SMCommand

  final case class ActionPerformed(msg: String) extends SMResponse
  final case class ActionFailed(err: String) extends SMResponse
  final case class Messages(userId: Long, messages: Seq[Message]) extends SMResponse
  final case class GetUserResponse(maybeUser: Option[User]) extends SMResponse

  def apply(persistentRepository: ActorRef[PersistentRepository.Command]): Behavior[Command] = Behaviors.receivePartial[Command] {
    case (ctx, command) => {

      implicit val timeout: Timeout = 3.seconds

      command match {
        case MsgFromRaftSystem(act, replyTo) =>
          act match {
            case CreateChat(userId, c) => Behaviors.same
            case GetUser(userId) => Behaviors.same
            case CreateUser(username) =>
              ctx.askWithStatus(persistentRepository, PersistentRepository.CreateUser(username, _)) {
                case Success(_) => WrappedResponse(ActionPerformed("User " + username + " created"), replyTo)
                case Failure(e) => WrappedResponse(ActionFailed(e.getMessage), replyTo)
              }
              Behaviors.same
            case SendMessageTo(userId: Long, receiverId: Long, content: String) => Behaviors.same
            case JoinChat(userId: Long, chatId: Long) => Behaviors.same
            case GetUserMessages(userId: Long, timestamp: java.time.Instant) => Behaviors.same
            case _ => Behaviors.unhandled
          }
        case WrappedResponse(result, replyTo) =>
          replyTo ! result
          Behaviors.same
      }
    }
  }


}
