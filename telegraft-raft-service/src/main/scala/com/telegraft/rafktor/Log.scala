package com.telegraft.rafktor
import com.fasterxml.jackson.annotation.{ JsonSubTypes, JsonTypeInfo }
import com.telegraft.rafktor.Log.{ LogEntry, TelegraftResponse }
import com.telegraft.rafktor.proto.{
  ClientQueryPayload,
  ClientQueryResponse,
  LogEntryResponse,
  LogEntryPayload => ProtoEntryPayload
}
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

/**
 * @param logEntries a log entry is a tuple comprised of a payload, term, an optional tuple with clientId and requestId
 *                   and an optional response, such response is none iff the payload hasn't been answered yet, or does
 *                   not need a response.
 */
final case class Log(logEntries: Vector[LogEntry]) extends CborSerializable {

  def apply(index: Int): LogEntry = logEntries(index)
  def length: Int = logEntries.length

  def lastLogIndex: Int = logEntries.length - 1
  def lastLogTerm: Long = if (logEntries.nonEmpty) logEntries.last.term else -1

  def appendEntry(
      term: Long,
      payload: Log.LogEntryPayLoad,
      clientRequest: Option[(String, String)] = None,
      telegraftResponse: Option[TelegraftResponse] = None): Log =
    this.copy(logEntries = this.logEntries :+ LogEntry(payload, term, clientRequest, telegraftResponse))

  def appendEntries(newEntries: Log, prevLogIndex: Int): Log =
    this.copy(logEntries = this.logEntries ++ newEntries.logEntries.drop(this.logEntries.length - 1 - prevLogIndex))

  def removeConflictingEntries(newEntries: Log, prevLogIndex: Int): Log = {
    this.copy(logEntries = this.logEntries.zipWithIndex
      .takeWhile {
        case (entry, index) if index > prevLogIndex =>
          !Log.entryIsConflicting(newEntries, index - prevLogIndex - 1, entry.term)
        case _ => true
      }
      .map(_._1))
  }

  def isMoreUpToDate(otherLastLogIndex: Int, otherLastLogTerm: Long): Boolean = {
    // if this last log term is higher then other log, this log is more up to date
    (this.lastLogTerm > otherLastLogTerm) ||
    // else the bigger log is more up to date
    (this.lastLogTerm == otherLastLogTerm && this.length - 1 > otherLastLogIndex)
  }

}
object Log {

  final case class LogEntry(
      payload: LogEntryPayLoad,
      term: Long,
      maybeClientId: Option[(String, String)],
      maybeResponse: Option[TelegraftResponse])
      extends CborSerializable

  def entryIsConflicting(conflictingLog: Log, index: Int, term: Long): Boolean = {

    try conflictingLog.logEntries(index).term != term
    catch {
      // if the entry is not part of the log entirely then it is not conflicting
      case _: Exception => false
    }
  }
  def empty: Log = Log(Vector.empty)

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(new JsonSubTypes.Type(value = classOf[TelegraftRequest], name = "telegraftRequest")))
  sealed trait LogEntryPayLoad {
    def convertToGrpc(): ProtoEntryPayload.Payload
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
  object TelegraftRequest {
    def convertToGrpc(request: TelegraftRequest): ProtoEntryPayload.Payload = {
      request match {
        case CreateUser(userName) => ProtoEntryPayload.Payload.CreateUser(CreateUserRequest(userName))
        case SendMessage(userId, chatId, content, timestamp) =>
          ProtoEntryPayload.Payload.SendMessage(
            SendMessageRequest(userId, chatId, content, Some(com.google.protobuf.timestamp.Timestamp(timestamp))))
        case CreateChat(userId, chatName, chatDescription) =>
          ProtoEntryPayload.Payload.CreateChat(CreateChatRequest(userId, chatName, chatDescription))
        case JoinChat(userId, chatId) => ProtoEntryPayload.Payload.JoinChat(JoinChatRequest(userId, chatId))
        case GetMessages(userId, timestamp) =>
          ProtoEntryPayload.Payload.GetMessages(
            GetMessagesRequest(userId, Some(com.google.protobuf.timestamp.Timestamp(timestamp))))
      }
    }

    def convertFromGrpc(request: ProtoEntryPayload.Payload): Option[TelegraftRequest] = {
      request match {
        case ProtoEntryPayload.Payload.Empty             => None
        case ProtoEntryPayload.Payload.CreateUser(value) => Some(CreateUser(value.username))
        case ProtoEntryPayload.Payload.SendMessage(value) =>
          Some(SendMessage(value.userId, value.chatId, value.content, value.getTimestamp.asJavaInstant))
        case ProtoEntryPayload.Payload.CreateChat(value) =>
          Some(CreateChat(value.userId, value.chatName, value.chatDescription))
        case ProtoEntryPayload.Payload.JoinChat(value) =>
          Some(JoinChat(value.userId, value.chatId))
        case ProtoEntryPayload.Payload.GetMessages(value) =>
          Some(GetMessages(value.userId, value.getMessagesAfter.asJavaInstant))
      }
    }
    def convertFromQueryGrpc(request: ClientQueryPayload.Payload): Option[TelegraftRequest] = {
      request match {
        case ClientQueryPayload.Payload.Empty => None
        case ClientQueryPayload.Payload.GetMessages(value) =>
          Some(GetMessages(value.userId, value.getMessagesAfter.asJavaInstant))
      }
    }
  }

  final case class CreateUser(userName: String) extends TelegraftRequest {
    override def convertToGrpc(): ProtoEntryPayload.Payload.CreateUser =
      ProtoEntryPayload.Payload.CreateUser(CreateUserRequest(userName))
  }

  final case class SendMessage(userId: String, chatId: String, content: String, timestamp: java.time.Instant)
      extends TelegraftRequest {
    override def convertToGrpc(): ProtoEntryPayload.Payload.SendMessage =
      ProtoEntryPayload.Payload.SendMessage(
        SendMessageRequest(userId, chatId, content, Some(com.google.protobuf.timestamp.Timestamp(timestamp))))
  }

  final case class CreateChat(userId: String, chatName: String, chatDescription: String) extends TelegraftRequest {
    override def convertToGrpc(): ProtoEntryPayload.Payload.CreateChat =
      ProtoEntryPayload.Payload.CreateChat(CreateChatRequest(userId, chatName, chatDescription))
  }

  final case class JoinChat(userId: String, chatId: String) extends TelegraftRequest {
    override def convertToGrpc(): ProtoEntryPayload.Payload.JoinChat =
      ProtoEntryPayload.Payload.JoinChat(JoinChatRequest(userId, chatId))
  }

  final case class GetMessages(userId: String, timestamp: java.time.Instant) extends TelegraftRequest {
    override def convertToGrpc(): ProtoEntryPayload.Payload.GetMessages =
      ProtoEntryPayload.Payload.GetMessages(
        GetMessagesRequest(userId, Some(com.google.protobuf.timestamp.Timestamp(timestamp))))
  }

  trait ConvertFromGrpc[T, S] {
    implicit def convertFromGrpc(grpc: T): S
  }

  final case class Message(userId: String, chatId: String, content: String, sentTime: java.time.Instant)
      extends CborSerializable {
    def convertToGrpc(): com.telegraft.statemachine.proto.Message =
      com.telegraft.statemachine.proto
        .Message(userId, chatId, content, Some(com.google.protobuf.timestamp.Timestamp(sentTime)))
  }

  object Message extends ConvertFromGrpc[ProtoMessage, Message] {
    override implicit def convertFromGrpc(grpc: ProtoMessage): Message =
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
  sealed trait TelegraftResponse
  object TelegraftResponse {
    def convertFromGrpc(response: LogEntryResponse.Payload): Option[TelegraftResponse] = {
      response match {
        case LogEntryResponse.Payload.Empty             => None
        case LogEntryResponse.Payload.CreateUser(value) => Some(UserCreated(value.ok, value.userId, value.errorMessage))
        case LogEntryResponse.Payload.SendMessage(value) =>
          Some(MessageSent(value.ok, Some(Message.convertFromGrpc(value.getMessage)), value.errorMessage))
        case LogEntryResponse.Payload.CreateChat(value) =>
          Some(ChatCreated(value.ok, value.chatId, value.errorMessage))
        case LogEntryResponse.Payload.JoinChat(value) => Some(ChatJoined(value.ok, value.errorMessage))
        case LogEntryResponse.Payload.GetMessages(value) =>
          Some(MessagesRetrieved(value.ok, value.messages.map(Message.convertFromGrpc).toSet, value.errorMessage))
      }
    }
    def convertToGrpc(response: TelegraftResponse): LogEntryResponse.Payload = {
      response match {
        case UserCreated(ok, userId, errMsg) =>
          LogEntryResponse.Payload.CreateUser(CreateUserResponse(ok, userId, errMsg))
        case MessageSent(ok, Some(msg), errMsg) =>
          LogEntryResponse.Payload.SendMessage(SendMessageResponse(ok, Some(msg.convertToGrpc()), errMsg))
        case MessageSent(ok, None, errMsg) =>
          LogEntryResponse.Payload.SendMessage(SendMessageResponse(ok, None, errMsg))
        case ChatCreated(ok, chatId, errMsg) =>
          LogEntryResponse.Payload.CreateChat(CreateChatResponse(ok, chatId, errMsg))
        case ChatJoined(ok, errMsg) => LogEntryResponse.Payload.JoinChat(JoinChatResponse(ok, errMsg))
        case MessagesRetrieved(ok, messages, errMsg) =>
          LogEntryResponse.Payload.GetMessages(GetMessagesResponse(ok, messages.map(_.convertToGrpc()).toSeq, errMsg))
      }
    }

    def convertToQueryGrpc(response: TelegraftResponse): ClientQueryResponse.Payload = {
      response match {

        case MessagesRetrieved(ok, messages, errMsg) =>
          ClientQueryResponse.Payload.GetMessages(
            GetMessagesResponse(ok, messages.map(_.convertToGrpc()).toSeq, errMsg))
        case _ => ClientQueryResponse.Payload.Empty
      }
    }
  }

  final case class UserCreated(ok: Boolean, userId: String, errMsg: Option[String]) extends TelegraftResponse

  object UserCreated extends ConvertFromGrpc[CreateUserResponse, UserCreated] {
    override implicit def convertFromGrpc(response: CreateUserResponse): UserCreated =
      UserCreated(response.ok, response.userId, response.errorMessage)

  }

  final case class MessageSent(ok: Boolean, msg: Option[Message], errMsg: Option[String]) extends TelegraftResponse
  object MessageSent extends ConvertFromGrpc[SendMessageResponse, MessageSent] {
    override implicit def convertFromGrpc(response: SendMessageResponse): MessageSent =
      MessageSent(response.ok, Some(Message.convertFromGrpc(response.getMessage)), response.errorMessage)
  }

  final case class ChatCreated(ok: Boolean, chatId: String, errMsg: Option[String]) extends TelegraftResponse

  object ChatCreated extends ConvertFromGrpc[CreateChatResponse, ChatCreated] {
    override implicit def convertFromGrpc(grpc: CreateChatResponse): ChatCreated =
      ChatCreated(grpc.ok, grpc.chatId, grpc.errorMessage)

  }

  final case class ChatJoined(ok: Boolean, errMsg: Option[String]) extends TelegraftResponse

  object ChatJoined extends ConvertFromGrpc[JoinChatResponse, ChatJoined] {
    override implicit def convertFromGrpc(grpc: JoinChatResponse): ChatJoined =
      ChatJoined(grpc.ok, grpc.errorMessage)
  }

  final case class MessagesRetrieved(ok: Boolean, messages: Set[Message], errMsg: Option[String])
      extends TelegraftResponse

  object MessagesRetrieved extends ConvertFromGrpc[GetMessagesResponse, MessagesRetrieved] {
    override implicit def convertFromGrpc(grpc: GetMessagesResponse): MessagesRetrieved =
      MessagesRetrieved(grpc.ok, grpc.messages.map(Message.convertFromGrpc).toSet, grpc.errorMessage)

  }
}
