package com.telegraft.statemachine

import scala.concurrent.{ ExecutionContext, Future }
import akka.actor.ActorSystem
import com.telegraft.statemachine.database.DatabaseRepository

import java.time.Instant

class TelegraftStateMachineImpl(repository: DatabaseRepository)(implicit val system: ActorSystem)
    extends proto.TelegraftStateMachineService {

  private implicit val ec: ExecutionContext = system.dispatcher

  def createUser(in: proto.CreateUserRequest): Future[proto.CreateUserResponse] =
    repository.userRepo
      .createUser(0, in.username)
      .map(i => proto.CreateUserResponse(ok = true, i, None))
      .recover(err => proto.CreateUserResponse(ok = false, -1, Some(err.getMessage)))

  def sendMessage(in: proto.SendMessageRequest): Future[proto.SendMessageResponse] =
    repository.messageRepo
      .createMessage(
        in.userId,
        in.chatId,
        in.content,
        Instant.ofEpochSecond(in.getTimestamp.seconds, in.getTimestamp.nanos))
      .map(_ =>
        proto.SendMessageResponse(ok = true, Some(proto.Message(in.userId, in.chatId, in.content, in.timestamp))))
      .recover(err => proto.SendMessageResponse(ok = false, None, Some(err.getMessage)))

  def createChat(in: proto.CreateChatRequest): Future[proto.CreateChatResponse] =
    repository
      .createChatWithUser(in.chatName, in.userId)
      .map(i => proto.CreateChatResponse(ok = true, i))
      .recover(err => proto.CreateChatResponse(ok = false, -1, Some(err.getMessage)))

  def joinChat(in: proto.JoinChatRequest): Future[proto.JoinChatResponse] =
    repository.userChatRepo
      .createUserChat(in.userId, in.chatId)
      .map(_ => proto.JoinChatResponse(ok = true))
      .recover(err => proto.JoinChatResponse(ok = false, Some(err.getMessage)))

  def getChatUsers(in: proto.GetChatUsersRequest): Future[proto.GetChatUsersResponse] = {
    repository
      .getChatUsers(in.chatId)
      .map(users => proto.GetChatUsersResponse(ok = true, users = users.map(x => proto.User(x.id, x.userName)), None))
      .recover(err => proto.GetChatUsersResponse(ok = false, users = Seq.empty, Some(err.getMessage)))
  }

  def getMessages(in: proto.GetMessagesRequest): Future[proto.GetMessagesResponse] = {

    repository
      .getMessagesAfterTimestamp(
        in.userId,
        Instant.ofEpochSecond(in.getMessagesAfter.seconds, in.getMessagesAfter.nanos))
      .map(messages => {
        val protoMessages = messages.map(x =>
          proto.Message(
            x.userId,
            x.chatId,
            x.content,
            Some(com.google.protobuf.timestamp.Timestamp.of(x.timestamp.getEpochSecond, x.timestamp.getNano))))
        proto.GetMessagesResponse(ok = true, protoMessages)
      })
      .recover(err => proto.GetMessagesResponse(ok = false, messages = Seq.empty, Some(err.getMessage)))
  }

}
