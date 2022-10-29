package com.telegraft.statemachine

import akka.Done

import scala.concurrent.{ ExecutionContextExecutor, Future, TimeoutException }
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.pattern.StatusReply
import akka.util.Timeout
import com.telegraft.statemachine.persistence.{ PersistentChat, PersistentUser }
import org.slf4j.LoggerFactory
import com.telegraft.statemachine.database.DatabaseRepository
import com.telegraft.statemachine.persistence.PersistentChat.{ Chat, Message }
import io.grpc.Status

import java.time.Instant
import scala.util.Success

class TelegraftStateMachineImpl(system: ActorSystem[_])
    extends proto.TelegraftStateMachineService {

  private val logger = LoggerFactory.getLogger(getClass)
  private val sharding = ClusterSharding(system)
  private implicit val ec: ExecutionContextExecutor = system.executionContext

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config
        .getDuration("telegraft-statemachine-service.ask-timeout"))

  def createUser(
      in: proto.CreateUserRequest): Future[proto.CreateUserResponse] = {

    val id = java.util.UUID.randomUUID
    val entity = sharding.entityRefFor(PersistentUser.EntityKey, id.toString)
    entity
      .askWithStatus(PersistentUser.CreateUser(in.username, _))
      .map(user => proto.CreateUserResponse(ok = true, user.userId))
      .recover(x =>
        proto.CreateUserResponse(ok = false, "", Some(x.getMessage)))

  }

  def sendMessage(
      in: proto.SendMessageRequest): Future[proto.SendMessageResponse] = {
    val entity = sharding.entityRefFor(PersistentChat.EntityKey, in.chatId)
    entity
      .ask(
        PersistentChat.SendMessageTo(
          in.userId,
          in.chatId,
          in.content,
          Instant.ofEpochSecond(in.getTimestamp.seconds, in.getTimestamp.nanos),
          _))
      .map {
        case StatusReply.Success(msg: Message) =>
          proto.SendMessageResponse(
            ok = true,
            Some(
              proto.Message(
                msg.sender,
                in.chatId,
                msg.content,
                Some(com.google.protobuf.timestamp.Timestamp
                  .of(msg.timestamp.getEpochSecond, msg.timestamp.getNano)))))
        case StatusReply.Error(msg) =>
          proto.SendMessageResponse(ok = false, None, Some(msg.getMessage))
      }
  }
  def createChat(
      in: proto.CreateChatRequest): Future[proto.CreateChatResponse] = {

    val id = java.util.UUID.randomUUID

    val entity = sharding.entityRefFor(PersistentChat.EntityKey, id.toString)
    entity
      .ask(
        PersistentChat
          .CreateChat(in.chatName, in.userId, in.chatDescription, _))
      .map {
        case StatusReply.Success(chat: Chat) =>
          proto.CreateChatResponse(ok = true, chat.id)
        case StatusReply.Error(msg) =>
          proto.CreateChatResponse(ok = false, "", Some(msg.getMessage))
      }

  }
  def joinChat(in: proto.JoinChatRequest): Future[proto.JoinChatResponse] = {
    val entity = sharding.entityRefFor(PersistentChat.EntityKey, in.chatId)

    entity.ask(PersistentChat.JoinChat(in.chatId, in.userId, _)).map {
      case StatusReply.Success(_: Done) => proto.JoinChatResponse(ok = true)
      case StatusReply.Error(msg) =>
        proto.JoinChatResponse(ok = false, Some(msg.getMessage))
    }
  }
  def getMessages(
      in: proto.GetMessagesRequest): Future[proto.GetMessagesResponse] = {
    // TODO
    DatabaseRepository
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
  }

}
