package com.telegraft

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import com.telegraft.database.Connection
import com.telegraft.database.model.{Chat, Message, User}
import com.telegraft.database.query.QueryRepository
import com.telegraft.rafktor.{SMCommand, SMResponse}

import scala.util.{Failure, Success}

object SMProtocol {

  sealed trait Command
  /** Request from RaftNode, to be computed and once finished acknowledged to RaftNode */
  final case class MsgFromRaftSystem(act: SMCommand, replyTo: ActorRef[SMResponse]) extends Command
  /** Wrapped response for Akka's pipeToSelf interaction pattern */
  private final case class WrappedResponse(result: SMResponse, replyTo: ActorRef[SMResponse]) extends Command

  sealed trait Action extends SMCommand
  final case class CreateChat(userId: Long, c: Chat) extends Action
  final case class GetUser(userId: Long) extends Action
  final case class CreateUser(userName: String, password: String) extends Action
  final case class SendMessageTo(userId: Long, receiverId: Long, content: String) extends Action
  final case class JoinChat(userId: Long, chatId: Long) extends Action
  final case class GetUserMessages(userId: Long, timestamp: java.time.Instant) extends Action

  sealed trait Response extends SMResponse
  final case class ActionPerformed(msg: String) extends Response
  final case class ActionFailed(err: String) extends Response
  final case class Messages(userId: Long, messages: Seq[Message]) extends Response
  final case class GetUserResponse(maybeUser: Option[User]) extends Response

  def apply(): Behavior[Command] = Behaviors.receivePartial[Command] {
    case (ctx, MsgFromRaftSystem(act: Action, replyTo: ActorRef[Response])) =>
      act match {
        case CreateChat(userId: Long, c: Chat) =>
          val futureResult = QueryRepository.apply(Connection.db).createUserChat(userId, c)
          ctx.pipeToSelf(futureResult) {
            case Success(_) => WrappedResponse(ActionPerformed("Chat " + c.chatName + " created with id"), replyTo)
            case Failure(e) => WrappedResponse(ActionFailed(e.getMessage), replyTo)
          }
          Behaviors.same
        case GetUser(userId: Long) => Behaviors.same
        case CreateUser(userName: String, password: String) =>
          val futureResult = QueryRepository.apply(Connection.db).createUser(User(0, userName, password))
          ctx.pipeToSelf(futureResult) {
            case Success(_) => WrappedResponse(ActionPerformed("User " + userName + " created"), replyTo)
            case Failure(e) => WrappedResponse(ActionFailed(e.getMessage), replyTo)
          }
          Behaviors.same
        case SendMessageTo(userId: Long, receiverId: Long, content: String) => Behaviors.same
        case JoinChat(userId: Long, chatId: Long) => Behaviors.same
        case GetUserMessages(userId: Long, timestamp: java.time.Instant) => Behaviors.same
      }
    case (_, WrappedResponse(result: Response, replyTo: ActorRef[Response])) => replyTo ! result; Behaviors.same
  }


}
