package com.telegraft.statemachine

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.telegraft.statemachine.persistence.{PersistentChat, PersistentUser}
import com.telegraft.statemachine.database.DatabaseRepository

import java.time.Instant

class TelegraftStateMachineImpl(
    system: ActorSystem[_],
    repository: DatabaseRepository)
    extends proto.TelegraftStateMachineService {

  private val sharding = ClusterSharding(system)
  private implicit val ec: ExecutionContext = system.executionContext

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config
        .getDuration("telegraft-statemachine-service.ask-timeout"))

  def createUser(
      in: proto.CreateUserRequest): Future[proto.CreateUserResponse] = {

    val id = java.util.UUID.randomUUID
    sharding
      .entityRefFor(PersistentUser.EntityKey, id.toString)
      .askWithStatus(PersistentUser.CreateUser(in.username, _))
      .map(user => proto.CreateUserResponse(ok = true, user.userId))
      .recover(err =>
        proto.CreateUserResponse(ok = false, "", Some(err.getMessage)))

  }

  def sendMessage(
      in: proto.SendMessageRequest): Future[proto.SendMessageResponse] = {
    sharding
      .entityRefFor(PersistentChat.EntityKey, in.chatId)
      .askWithStatus(
        PersistentChat.SendMessageTo(
          in.userId,
          in.chatId,
          in.content,
          Instant.ofEpochSecond(in.getTimestamp.seconds, in.getTimestamp.nanos),
          _))
      .map(msg =>
        proto.SendMessageResponse(
          ok = true,
          Some(
            proto.Message(
              msg.userId,
              in.chatId,
              msg.content,
              Some(com.google.protobuf.timestamp.Timestamp
                .of(msg.timestamp.getEpochSecond, msg.timestamp.getNano))))))
      .recover(err =>
        proto.SendMessageResponse(ok = false, None, Some(err.getMessage)))
  }

  def createChat(
      in: proto.CreateChatRequest): Future[proto.CreateChatResponse] = {

    val id = java.util.UUID.randomUUID
    sharding
      .entityRefFor(PersistentChat.EntityKey, id.toString)
      .askWithStatus(PersistentChat
        .CreateChat(in.chatName, in.userId, in.chatDescription, _))
      .map(chat => proto.CreateChatResponse(ok = true, chat.id))
      .recover(err =>
        proto.CreateChatResponse(ok = false, "", Some(err.getMessage)))
  }

  def joinChat(in: proto.JoinChatRequest): Future[proto.JoinChatResponse] = {
    sharding
      .entityRefFor(PersistentChat.EntityKey, in.chatId)
      .askWithStatus(PersistentChat.JoinChat(in.chatId, in.userId, _))
      .map(_ => proto.JoinChatResponse(ok = true))
      .recover(err => proto.JoinChatResponse(ok = false, Some(err.getMessage)))
  }

  def getChatUsers(in: proto.GetChatUsersRequest): Future[proto.GetChatUsersResponse] = {
    repository.getChatUsers(in.chatId).map(users =>
      proto.GetChatUsersResponse(ok = true, users = users.map(x => proto.User(x.id, x.userName)), None)
    ) .recover(err => proto.GetChatUsersResponse(ok = false, users = Seq.empty, Some(err.getMessage)))
  }

  def getMessages(
      in: proto.GetMessagesRequest): Future[proto.GetMessagesResponse] = {

    repository
      .getMessagesAfterTimestamp(
        in.userId,
        Instant.ofEpochSecond(
          in.getMessagesAfter.seconds,
          in.getMessagesAfter.nanos))
      .map(messages => {
        val protoMessages = messages.map(x =>
          proto.Message(
            x.userId,
            x.chatId,
            x.content,
            Some(
              com.google.protobuf.timestamp.Timestamp
                .of(x.timestamp.getEpochSecond, x.timestamp.getNano))))
        proto.GetMessagesResponse(ok = true, protoMessages)
      })
      .recover(err => proto.GetMessagesResponse(ok = false, messages = Seq.empty, Some(err.getMessage)))
  }

}
