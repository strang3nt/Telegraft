package com.telegraft.rafktor

import com.telegraft.rafktor.Log.LogEntryPayLoad
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
import scala.collection.immutable

final case class Log(logEntries: immutable.Vector[(LogEntryPayLoad, Long)]) {

  def appendEntry(term: Long, payload: Log.LogEntryPayLoad): Log =
    this.copy(logEntries = this.logEntries :+ (payload, term))

  def appendEntries(newEntries: Log, prevLogIndex: Int): Log =
    this.copy(logEntries = this.logEntries ++ newEntries.logEntries.drop(
      this.logEntries.length - 1 - prevLogIndex))

  def entryIsConflicting(log: Log, index: Int, term: Long): Boolean =
    log.logEntries(index)._2 != term

  def removeConflictingEntries(newEntries: Log, prevLogIndex: Int): Log = {
    this.copy(logEntries = this.logEntries.zipWithIndex
      .takeWhile {
        case (entry, index) if index > prevLogIndex =>
          !entryIsConflicting(newEntries, index - prevLogIndex - 1, entry._2)
        case _ => true
      }
      .map(_._1))
  }

  def isMoreUpToDate(
      otherLastLogIndex: Int,
      otherLastLogTerm: Long): Boolean = {
    // if this last log term is higher then other log, this log is more up to date
    (this.logEntries.last._2 > otherLastLogTerm) ||
    // else the bigger log is more up to date
    (this.logEntries.last._2 == otherLastLogTerm && this.logEntries.length - 1 > otherLastLogIndex)
  }

}
object Log {

  def empty: Log = Log(Vector.empty[(LogEntryPayLoad, Long)])
  sealed trait LogEntryPayLoad {
    def convertToGRPC: com.telegraft.rafktor.proto.LogEntryPayload.Payload
  }

  sealed trait TelegraftRequest extends LogEntryPayLoad

  final case class CreateUser(userName: String) extends TelegraftRequest {
    override def convertToGRPC: ProtoLogEntryPayload.Payload.CreateUser =
      ProtoLogEntryPayload.Payload.CreateUser(CreateUserRequest(userName))
  }

  final case class SendMessage(
      userId: String,
      chatId: String,
      content: String,
      timestamp: java.time.Instant)
      extends TelegraftRequest {
    override def convertToGRPC: ProtoLogEntryPayload.Payload.SendMessage =
      ProtoLogEntryPayload.Payload.SendMessage(
        SendMessageRequest(
          userId,
          chatId,
          content,
          Some(com.google.protobuf.timestamp.Timestamp(timestamp))))
  }

  final case class CreateChat(
      userId: String,
      chatName: String,
      chatDescription: String)
      extends TelegraftRequest {
    override def convertToGRPC: ProtoLogEntryPayload.Payload.CreateChat =
      ProtoLogEntryPayload.Payload.CreateChat(
        CreateChatRequest(userId, chatName, chatDescription))
  }

  final case class JoinChat(userId: String, chatId: String)
      extends TelegraftRequest {
    override def convertToGRPC: ProtoLogEntryPayload.Payload.JoinChat =
      ProtoLogEntryPayload.Payload.JoinChat(JoinChatRequest(userId, chatId))
  }

  final case class GetMessages(userId: String, timestamp: java.time.Instant)
      extends TelegraftRequest {
    override def convertToGRPC: ProtoLogEntryPayload.Payload.GetMessage =
      ProtoLogEntryPayload.Payload.GetMessage(
        GetMessagesRequest(
          userId,
          Some(com.google.protobuf.timestamp.Timestamp(timestamp))))
  }

  trait ConvertFromGRPC[T, S] {
    implicit def convertFromGRPC(grpc: T): S
  }

  final case class Message(
      userId: String,
      chatId: String,
      content: String,
      sentTime: java.time.Instant)
      extends CborSerializable
  object Message extends ConvertFromGRPC[ProtoMessage, Message] {
    override implicit def convertFromGRPC(grpc: ProtoMessage): Message =
      Message(
        grpc.userId,
        grpc.chatId,
        grpc.content,
        grpc.sentTime.get.asJavaInstant)
  }

  sealed trait TelegraftResponse

  final case class UserCreated(
      ok: Boolean,
      userId: String,
      errMsg: Option[String])
      extends TelegraftResponse
  object UserCreated extends ConvertFromGRPC[CreateUserResponse, UserCreated] {
    override implicit def convertFromGRPC(
        response: CreateUserResponse): UserCreated =
      UserCreated(response.ok, response.userId, response.errorMessage)

  }
  final case class MessageSent(
      ok: Boolean,
      msg: Option[Message],
      errMsg: Option[String])
      extends TelegraftResponse
  object MessageSent extends ConvertFromGRPC[SendMessageResponse, MessageSent] {
    override implicit def convertFromGRPC(
        response: SendMessageResponse): MessageSent =
      MessageSent(
        response.ok,
        Some(Message.convertFromGRPC(response.getMessage)),
        response.errorMessage)
  }

  final case class ChatCreated(
      ok: Boolean,
      chatId: String,
      errMsg: Option[String])
      extends TelegraftResponse

  object ChatCreated extends ConvertFromGRPC[CreateChatResponse, ChatCreated] {
    override implicit def convertFromGRPC(
        grpc: CreateChatResponse): ChatCreated =
      ChatCreated(grpc.ok, grpc.chatId, grpc.errorMessage)

  }

  final case class ChatJoined(ok: Boolean, errMsg: Option[String])
      extends TelegraftResponse
  object ChatJoined extends ConvertFromGRPC[JoinChatResponse, ChatJoined] {
    override implicit def convertFromGRPC(grpc: JoinChatResponse): ChatJoined =
      ChatJoined(grpc.ok, grpc.errorMessage)
  }

  final case class MessagesRetrieved(
      ok: Boolean,
      messages: Set[Message],
      errMsg: Option[String])
      extends TelegraftResponse
  object MessagesRetrieved
      extends ConvertFromGRPC[GetMessagesResponse, MessagesRetrieved] {
    override implicit def convertFromGRPC(
        grpc: GetMessagesResponse): MessagesRetrieved =
      MessagesRetrieved(
        grpc.ok,
        grpc.messages.map(Message.convertFromGRPC).toSet,
        grpc.errorMessage)

  }
}
