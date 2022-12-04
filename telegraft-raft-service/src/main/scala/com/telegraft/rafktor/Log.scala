package com.telegraft.rafktor

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import com.fasterxml.jackson.annotation.{ JsonSubTypes, JsonTypeInfo }
import com.telegraft.rafktor.Log.{ LogEntryPayLoad, TelegraftResponse }
import com.telegraft.rafktor.proto.{ LogEntryPayload => ProtoLogEntryPayload }
import com.telegraft.statemachine.proto.{
  CreateChatRequest,
  CreateChatResponse,
  CreateUserRequest,
  CreateUserResponse,
  GetMessagesRequest,
  GetMessagesResponse,
  JoinChatRequest,
  JoinChatResponse,
  SendMessageRequest,
  SendMessageResponse,
  Message => ProtoMessage
}

final case class Log(val logEntries: Vector[(LogEntryPayLoad, Long)]) extends CborSerializable {

  def apply(index: Int): (LogEntryPayLoad, Long) = logEntries(index)
  def length: Int = logEntries.length

  def lastLogIndex: Int = logEntries.length - 1
  def lastLogTerm: Long = if (logEntries.nonEmpty) logEntries.last._2 else -1

  def appendEntry(term: Long, payload: Log.LogEntryPayLoad): Log =
    this.copy(logEntries = this.logEntries :+ (payload, term))

  def appendEntries(newEntries: Log, prevLogIndex: Int): Log =
    this.copy(logEntries = this.logEntries ++ newEntries.logEntries.drop(this.logEntries.length - 1 - prevLogIndex))

  def removeConflictingEntries(newEntries: Log, prevLogIndex: Int): Log = {
    this.copy(logEntries = this.logEntries.zipWithIndex
      .takeWhile {
        case (entry, index) if index > prevLogIndex =>
          !Log.entryIsConflicting(newEntries, index - prevLogIndex - 1, entry._2)
        case _ => true
      }
      .map(_._1))
  }

  def isMoreUpToDate(otherLastLogIndex: Int, otherLastLogTerm: Long): Boolean = {
    // if this last log term is higher then other log, this log is more up to date
    (this.logEntries.last._2 > otherLastLogTerm) ||
    // else the bigger log is more up to date
    (this.logEntries.last._2 == otherLastLogTerm && this.logEntries.length - 1 > otherLastLogIndex)
  }

}
object Log {

  def entryIsConflicting(conflictingLog: Log, index: Int, term: Long): Boolean = {

    try conflictingLog.logEntries(index)._2 != term
    catch {
      // if the entry is not part of the log entirely then it is not conflicting
      case _: Exception => false
    }
  }
  def empty: Log = Log(Vector.empty[(LogEntryPayLoad, Long)])

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(new JsonSubTypes.Type(value = classOf[TelegraftRequest], name = "lion")))
  sealed trait LogEntryPayLoad {
    def convertToGRPC: com.telegraft.rafktor.proto.LogEntryPayload.Payload
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(
    Array(
      new JsonSubTypes.Type(value = classOf[CreateUser], name = "createUser"),
      new JsonSubTypes.Type(value = classOf[SendMessage], name = "sendMessage"),
      new JsonSubTypes.Type(value = classOf[JoinChat], name = "joinChat"),
      new JsonSubTypes.Type(value = classOf[CreateChat], name = "createChat"),
      new JsonSubTypes.Type(value = classOf[GetMessages], name = "getMessages")))
  sealed trait TelegraftRequest extends LogEntryPayLoad

  final case class CreateUser(userName: String) extends TelegraftRequest {
    override def convertToGRPC: ProtoLogEntryPayload.Payload.CreateUser =
      ProtoLogEntryPayload.Payload.CreateUser(CreateUserRequest(userName))
  }

  final case class SendMessage(userId: String, chatId: String, content: String, timestamp: java.time.Instant)
      extends TelegraftRequest {
    override def convertToGRPC: ProtoLogEntryPayload.Payload.SendMessage =
      ProtoLogEntryPayload.Payload.SendMessage(
        SendMessageRequest(userId, chatId, content, Some(com.google.protobuf.timestamp.Timestamp(timestamp))))
  }

  final case class CreateChat(userId: String, chatName: String, chatDescription: String) extends TelegraftRequest {
    override def convertToGRPC: ProtoLogEntryPayload.Payload.CreateChat =
      ProtoLogEntryPayload.Payload.CreateChat(CreateChatRequest(userId, chatName, chatDescription))
  }

  final case class JoinChat(userId: String, chatId: String) extends TelegraftRequest {
    override def convertToGRPC: ProtoLogEntryPayload.Payload.JoinChat =
      ProtoLogEntryPayload.Payload.JoinChat(JoinChatRequest(userId, chatId))
  }

  final case class GetMessages(userId: String, timestamp: java.time.Instant) extends TelegraftRequest {
    override def convertToGRPC: ProtoLogEntryPayload.Payload.GetMessage =
      ProtoLogEntryPayload.Payload.GetMessage(
        GetMessagesRequest(userId, Some(com.google.protobuf.timestamp.Timestamp(timestamp))))
  }

  trait ConvertFromGRPC[T, S] {
    implicit def convertFromGRPC(grpc: T): S
  }

  final case class Message(userId: String, chatId: String, content: String, sentTime: java.time.Instant)
  object Message extends ConvertFromGRPC[ProtoMessage, Message] {
    override implicit def convertFromGRPC(grpc: ProtoMessage): Message =
      Message(grpc.userId, grpc.chatId, grpc.content, grpc.sentTime.get.asJavaInstant)
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(
    Array(
      new JsonSubTypes.Type(value = classOf[UserCreated], name = "userCreated"),
      new JsonSubTypes.Type(value = classOf[MessageSent], name = "messageSent"),
      new JsonSubTypes.Type(value = classOf[ChatCreated], name = "chatCreated"),
      new JsonSubTypes.Type(value = classOf[ChatJoined], name = "chatJoined"),
      new JsonSubTypes.Type(value = classOf[MessagesRetrieved], name = "messageRetrieved")))
  sealed trait TelegraftResponse extends CborSerializable

  final case class UserCreated(ok: Boolean, userId: String, errMsg: Option[String]) extends TelegraftResponse
  object UserCreated extends ConvertFromGRPC[CreateUserResponse, UserCreated] {
    override implicit def convertFromGRPC(response: CreateUserResponse): UserCreated =
      UserCreated(response.ok, response.userId, response.errorMessage)

  }
  final case class MessageSent(ok: Boolean, msg: Option[Message], errMsg: Option[String]) extends TelegraftResponse
  object MessageSent extends ConvertFromGRPC[SendMessageResponse, MessageSent] {
    override implicit def convertFromGRPC(response: SendMessageResponse): MessageSent =
      MessageSent(response.ok, Some(Message.convertFromGRPC(response.getMessage)), response.errorMessage)
  }

  final case class ChatCreated(ok: Boolean, chatId: String, errMsg: Option[String]) extends TelegraftResponse

  object ChatCreated extends ConvertFromGRPC[CreateChatResponse, ChatCreated] {
    override implicit def convertFromGRPC(grpc: CreateChatResponse): ChatCreated =
      ChatCreated(grpc.ok, grpc.chatId, grpc.errorMessage)

  }

  final case class ChatJoined(ok: Boolean, errMsg: Option[String]) extends TelegraftResponse
  object ChatJoined extends ConvertFromGRPC[JoinChatResponse, ChatJoined] {
    override implicit def convertFromGRPC(grpc: JoinChatResponse): ChatJoined =
      ChatJoined(grpc.ok, grpc.errorMessage)
  }

  final case class MessagesRetrieved(ok: Boolean, messages: Set[Message], errMsg: Option[String])
      extends TelegraftResponse
  object MessagesRetrieved extends ConvertFromGRPC[GetMessagesResponse, MessagesRetrieved] {
    override implicit def convertFromGRPC(grpc: GetMessagesResponse): MessagesRetrieved =
      MessagesRetrieved(grpc.ok, grpc.messages.map(Message.convertFromGRPC).toSet, grpc.errorMessage)

  }
}
