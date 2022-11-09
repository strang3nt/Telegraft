package com.telegraft.rafktor

import akka.parboiled2.RuleTrace.StringMatch

sealed trait TelegraftRequest
final case class CreateUser(userName: String) extends TelegraftRequest
final case class SendMessage(
    userId: String,
    chatId: String,
    content: String,
    timestamp: java.time.Instant)
    extends TelegraftRequest
final case class CreateChat(
    userId: String,
    chatName: String,
    chatDescription: String)
    extends TelegraftRequest
final case class JoinChat(userId: String, chatId: String)
    extends TelegraftRequest
final case class GetMessages(userId: String, timestamp: java.time.Instant)
    extends TelegraftRequest

final case class Message(
    userId: String,
    chatId: String,
    content: String,
    sentTime: java.time.Instant)

sealed trait TelegraftResponse
final case class UserCreated(
    ok: Boolean,
    userId: String,
    errMsg: Option[String])
    extends TelegraftResponse
final case class MessageSent(
    ok: Boolean,
    msg: Option[Message],
    errMsg: Option[String])
    extends TelegraftResponse
final case class ChatCreated(
    ok: Boolean,
    chatId: String,
    errMsg: Option[String])
    extends TelegraftResponse
final case class ChatJoined(ok: Boolean, errMsg: Option[String])
    extends TelegraftResponse
final case class MessagesRetrieved(
    ok: Boolean,
    messages: Set[Message],
    errMsg: Option[String])
    extends TelegraftResponse
