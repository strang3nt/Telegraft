package com.telegraft

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.telegraft.persistence._
import com.telegraft.rafktor.{SMCommand, SMResponse}
import akka.actor.typed.scaladsl.AskPattern._

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object SMProtocol {

  final case class Chat(userId: String, name: String, description: String)
  final case class Message(userId: String, chatId: Int, content: String)
  final case class User(username: String)

  sealed trait Command
  /** Request from RaftNode, to be computed and once finished acknowledged to RaftNode */
  final case class MsgFromRaftSystem(act: SMCommand, replyTo: ActorRef[SMResponse]) extends Command
  /** Wrapped response for Akka's pipeToSelf interaction pattern */
  private final case class WrappedResponse(result: SMResponse, replyTo: ActorRef[SMResponse]) extends Command

  sealed trait Action extends SMCommand
  final case class CreateChat(userId: Long, c: Chat) extends Action
  final case class GetUser(userId: Long) extends Action
  final case class CreateUser(userName: String) extends Action
  final case class SendMessageTo(userId: Long, receiverId: Long, content: String) extends Action
  final case class JoinChat(userId: Long, chatId: Long) extends Action
  final case class GetUserMessages(userId: Long, timestamp: java.time.Instant) extends Action

  sealed trait Response extends SMResponse
  final case class ActionPerformed(msg: String) extends Response
  final case class ActionFailed(err: String) extends Response
  final case class Messages(userId: Long, messages: Seq[Message]) extends Response
  final case class GetUserResponse(maybeUser: Option[User]) extends Response

  def apply(persistentRepository: ActorRef[PersistentRepository.Command]): Behavior[Command] = Behaviors.receivePartial[Command] {
    case (ctx, command) => {

      implicit val timeout: Timeout = 3.seconds

      command match {
        case MsgFromRaftSystem(act: Action, replyTo: ActorRef[Response]) =>
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
          }
        case WrappedResponse(result: Response, replyTo: ActorRef[Response]) =>
          replyTo ! result
          Behaviors.same
      }
    }
  }


}
