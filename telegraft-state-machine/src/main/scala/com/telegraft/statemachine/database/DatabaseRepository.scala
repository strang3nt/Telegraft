package com.telegraft.statemachine.database

import akka.Done
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

object DatabaseRepository {

  private val dbConfig: DatabaseConfig[PostgresProfile] = Connection.dbConfig
  import ExecutionContext.Implicits.global

  val chatRepo = new ChatRepository(dbConfig)
  val userRepo = new UserRepository(dbConfig)
  val messageRepo =
    new MessageRepository(dbConfig, chatRepo, userRepo)
  val userChatRepo =
    new UserChatRepository(dbConfig, chatRepo, userRepo)

  def createTable: Future[Unit] = dbConfig.db.run {
    DBIO
      .seq(
        chatRepo.chatTable.schema.createIfNotExists,
        userRepo.userTable.schema.createIfNotExists,
        messageRepo.messageTable.schema.createIfNotExists,
        userChatRepo.userChatTable.schema.createIfNotExists)
      .transactionally
  }

  private def getUserChatsQuery(userId: String)
      : Query[DatabaseRepository.chatRepo.ChatTable, Chat, Seq] = {
    userChatRepo.userChatTable
      .filter(_.userId === userId)
      .join(chatRepo.chatTable)
      .on(_.userId === _.id)
      .map(_._2)
  }

  def createChatWithUser(
      chatId: String,
      chatName: String,
      userId: String): DBIO[Done] =
    DBIO
      .seq(
        chatRepo.createChat(Chat(chatId, chatName)),
        userChatRepo.createUserChat(chatId, userId))
      .transactionally
      .map(_ => Done)

  def getUserChats(userId: String): Future[Seq[Chat]] = {

    dbConfig.db.run {
      getUserChatsQuery(userId).result
    }
  }

  def getMessagesAfterTimestamp(
      userId: String,
      timestamp: Instant): Future[Seq[Message]] =
    dbConfig.db.run {
      getUserChatsQuery(userId)
        .join(messageRepo.messageTable)
        .on(_.id === _.chatId)
        .filter(_._2.timestamp >= timestamp)
        .map(_._2)
        .result
    }

}
