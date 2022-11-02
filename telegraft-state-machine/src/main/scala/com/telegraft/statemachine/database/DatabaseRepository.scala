package com.telegraft.statemachine.database

import akka.Done
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

trait DatabaseRepository {

  val chatRepo: ChatRepository
  val userRepo: UserRepository
  val messageRepo: MessageRepository
  val userChatRepo: UserChatRepository

  def createTables: Future[Unit]
  def createChatWithUser(
      chatId: String,
      chatName: String,
      userId: String): DBIO[Done]
  def getUserChats(userId: String): Future[Seq[Chat]]

  def getMessagesAfterTimestamp(
      userId: String,
      timestamp: Instant): Future[Seq[Message]]
}

object DatabaseRepositoryImpl {

  import ExecutionContext.Implicits.global
  def init: DatabaseRepository = {

    val chatRepo = new ChatRepository(Connection.dbConfig)
    val userRepo = new UserRepository(Connection.dbConfig)
    val messageRepo =
      new MessageRepository(Connection.dbConfig, chatRepo, userRepo)
    val userChatRepo =
      new UserChatRepository(Connection.dbConfig, chatRepo, userRepo)

    val databaseRepo =
      new DatabaseRepositoryImpl(chatRepo, userRepo, messageRepo, userChatRepo)
    databaseRepo.createTables
    databaseRepo
  }
}
class DatabaseRepositoryImpl(
    val chatRepo: ChatRepository,
    val userRepo: UserRepository,
    val messageRepo: MessageRepository,
    val userChatRepo: UserChatRepository)
    extends DatabaseRepository {

  import ExecutionContext.Implicits.global

  override def createTables: Future[Unit] = Connection.dbConfig.db.run {
    DBIO
      .seq(
        chatRepo.chatTable.schema.createIfNotExists,
        userRepo.userTable.schema.createIfNotExists,
        messageRepo.messageTable.schema.createIfNotExists,
        userChatRepo.userChatTable.schema.createIfNotExists)
      .transactionally
  }

  private def getUserChatsQuery(userId: String) = {
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

    Connection.dbConfig.db.run {
      getUserChatsQuery(userId).result
    }
  }

  override def getMessagesAfterTimestamp(
      userId: String,
      timestamp: Instant): Future[Seq[Message]] =
    Connection.dbConfig.db.run {
      getUserChatsQuery(userId)
        .join(messageRepo.messageTable)
        .on(_.id === _.chatId)
        .filter(_._2.timestamp >= timestamp)
        .map(_._2)
        .result
    }
}
