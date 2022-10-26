package com.telegraft.statemachine

import scala.concurrent.Future
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.pattern.StatusReply
import akka.util.Timeout
import com.telegraft.statemachine.persistence.{ PersistentChat, PersistentUser }
import org.slf4j.LoggerFactory
import com.telegraft.statemachine.database.DatabaseRepository

import java.time.Instant

class TelegraftStateMachineImpl(system: ActorSystem[_])
    extends proto.TelegraftStateMachineService {

  private val logger = LoggerFactory.getLogger(getClass)
  private val sharding = ClusterSharding(system)
  private implicit val ec = system.executionContext

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config
        .getDuration("telegraft-statemachine-service.ask-timeout"))

  def createUser(
      in: proto.CreateUserRequest): Future[proto.CreateUserResponse] = {

    val id = java.util.UUID.randomUUID
    val entity = sharding.entityRefFor(PersistentUser.EntityKey, id.toString)

    entity.ask(PersistentUser.CreateUser(in.username, _)).map {
      case StatusReply.Success(user: PersistentUser.User) =>
        proto.CreateUserResponse(true, user.userId)
      case StatusReply.Error(msg) =>
        proto.CreateUserResponse(false, "", Some(msg.getMessage))
    }
  }

  def sendMessage(
      in: proto.SendMessageRequest): Future[proto.SendMessageResponse] = {
    val entity = sharding.entityRefFor(PersistentChat.EntityKey, in.receiver)
    entity
      .ask(
        PersistentChat.SendMessageTo(
          in.sender,
          in.receiver,
          in.content,
          Instant.ofEpochSecond(in.getTimestamp.seconds, in.getTimestamp.nanos),
          _))
      .map {
        case StatusReply.Success(_) =>
          proto.SendMessageResponse(true, in.sender, in.receiver)
        case StatusReply.Error(msg) =>
          proto.SendMessageResponse(
            false,
            in.sender,
            in.receiver,
            Some(msg.getMessage))
      }
  }
  def createChat(createChatRequest: proto.CreateChatRequest)
      : Future[proto.CreateChatResponse] = ???
  def joinChat(
      joinChatRequest: proto.JoinChatRequest): Future[proto.JoinChatResponse] =
    ???
  def getMessages(
      in: proto.GetMessagesRequest): Future[proto.GetMessagesResponse] = {
    // TODO
//    DatabaseRepository.getMessagesAfterTimestamp(
//      in.user,
//      Instant
//        .ofEpochSecond(in.getMessagesAfter.seconds, in.getMessagesAfter.nanos))
    ???
  }
}
