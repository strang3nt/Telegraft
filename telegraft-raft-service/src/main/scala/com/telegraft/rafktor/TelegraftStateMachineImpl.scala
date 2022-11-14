package com.telegraft.statemachine.proto
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ ActorRef, ActorSystem, Scheduler }
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import com.telegraft.rafktor._
import io.grpc.Status

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContext, Future, TimeoutException }

/**
 * @param raftNode the node which requests will be repackaged and sent to
 *
 * Receives clients requests
 */
class TelegraftStateMachineImpl(raftNode: ActorRef[RaftServer.Command])(
    implicit val system: ActorSystem[_])
    extends TelegraftStateMachineService {

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val scheduler: Scheduler = system.scheduler
  private implicit val timeout: Timeout = 3.seconds

  override def createUser(in: CreateUserRequest): Future[CreateUserResponse] = {
    val response = raftNode
      .askWithStatus(RaftServer.ClientRequest(Log.CreateUser(in.username), _))
      .map {
        case Log.UserCreated(ok, userId, errMsg) =>
          CreateUserResponse(ok, userId, errMsg)
        case r =>
          throw new Exception(
            "Expected a UserCreated response, received: " + r.toString)
      }
    convertError(response)
  }

  private def javaInstantToGoogleTimestamp(inst: java.time.Instant) = {
    Some(
      com.google.protobuf.timestamp.Timestamp
        .of(inst.getEpochSecond, inst.getNano))
  }

  private def googleTimestampToJavaInstant(
      timestamp: com.google.protobuf.timestamp.Timestamp) = {
    java.time.Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos)
  }

  override def sendMessage(
      in: SendMessageRequest): Future[SendMessageResponse] = {
    val response = raftNode
      .askWithStatus(
        RaftServer.ClientRequest(
          Log.SendMessage(
            in.userId,
            in.chatId,
            in.content,
            googleTimestampToJavaInstant(in.getTimestamp)),
          _))
      .map {
        case Log.MessageSent(ok, None, errMsg) =>
          SendMessageResponse(ok, None, errMsg)
        case Log.MessageSent(ok, Some(msg), errMsg) =>
          SendMessageResponse(
            ok,
            Some(
              com.telegraft.statemachine.proto.Message(
                msg.userId,
                msg.chatId,
                msg.content,
                javaInstantToGoogleTimestamp(msg.sentTime))),
            errMsg)
        case r =>
          throw new Exception(
            "Expected a MessageSent response, received: " + r.toString)
      }
    convertError(response)
  }

  override def createChat(in: CreateChatRequest): Future[CreateChatResponse] = {
    val response = raftNode
      .askWithStatus(
        RaftServer.ClientRequest(
          Log.CreateChat(in.userId, in.chatName, in.chatDescription),
          _))
      .map {
        case Log.ChatCreated(ok, chatId, errMsg) =>
          CreateChatResponse(ok, chatId, errMsg)
        case r =>
          throw new Exception(
            "Expected a ChatCreated response, received: " + r.toString)
      }
    convertError(response)
  }

  override def joinChat(in: JoinChatRequest): Future[JoinChatResponse] = {
    val response = raftNode
      .askWithStatus(
        RaftServer.ClientRequest(Log.JoinChat(in.userId, in.chatId), _))
      .map {
        case Log.ChatJoined(ok, errMsg) =>
          JoinChatResponse(ok, errMsg)
        case r =>
          throw new Exception(
            "Expected a ChatJoined response, received: " + r.toString)
      }
    convertError(response)
  }

  override def getMessages(
      in: GetMessagesRequest): Future[GetMessagesResponse] = {
    val response = raftNode
      .askWithStatus(
        RaftServer.ClientRequest(
          Log.GetMessages(
            in.userId,
            googleTimestampToJavaInstant(in.getMessagesAfter)),
          _))
      .map {
        case Log.MessagesRetrieved(ok, messages, errMsg) =>
          GetMessagesResponse(
            ok,
            messages
              .map(m =>
                com.telegraft.statemachine.proto.Message(
                  m.userId,
                  m.chatId,
                  m.content,
                  javaInstantToGoogleTimestamp(m.sentTime)))
              .toSeq,
            errMsg)
        case r =>
          throw new Exception(
            "Expected a MessagesRetrieved response, received: " + r.toString)
      }
    convertError(response)
  }

  private def convertError[T](response: Future[T]): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(
          new GrpcServiceException(
            Status.UNAVAILABLE.withDescription("Operation timed out")))
      case exc =>
        Future.failed(
          new GrpcServiceException(
            Status.INVALID_ARGUMENT.withDescription(exc.getMessage)))
    }
  }
}
