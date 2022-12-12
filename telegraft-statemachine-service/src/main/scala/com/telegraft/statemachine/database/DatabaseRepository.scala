package com.telegraft.statemachine.database

import slick.jdbc.PostgresProfile.api._

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

trait DatabaseRepository {

  val chatRepo: ChatRepository
  val userRepo: UserRepository
  val messageRepo: MessageRepository
  val userChatRepo: UserChatRepository

  def createTables: Future[Unit]
  def createChatWithUser(chatName: String, userId: Long): Future[Long]
  def getUserChats(userId: Long): Future[Seq[Chat]]

  def getMessagesAfterTimestamp(userId: Long, timestamp: Instant): Future[Seq[Message]]

  def getChatUsers(chatId: Long): Future[Seq[User]]
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

  override def getChatUsers(chatId: Long): Future[Seq[User]] =
    Connection.dbConfig.db.run {
      userChatRepo.userChatTable
        .filter(_.chatId === chatId)
        .join(chatRepo.chatTable)
        .on(_.chatId === _.id)
        .join(userRepo.userTable)
        .on(_._1.userId === _.id)
        .map(_._2)
        .result
    }

  override def createTables: Future[Unit] = Connection.dbConfig.db.run {
    DBIO
      .seq(
        chatRepo.chatTable.schema.createIfNotExists,
        userRepo.userTable.schema.createIfNotExists,
        messageRepo.messageTable.schema.createIfNotExists,
        userChatRepo.userChatTable.schema.createIfNotExists)
      .transactionally
  }

  private def getUserChatsQuery(userId: Long) = {
    userChatRepo.userChatTable.filter(_.userId === userId).join(chatRepo.chatTable).on(_.userId === _.id).map(_._2)
  }

  def createChatWithUser(chatName: String, userId: Long): Future[Long] =
    Connection.dbConfig.db.run {
      (for {
        i <- chatRepo.chatTable.returning(chatRepo.chatTable.map(_.id)) += Chat(0, chatName)
        _ <- userChatRepo.userChatTable += UserChat(userId, i)
      } yield i).transactionally
    }

  def getUserChats(userId: Long): Future[Seq[Chat]] = {

    Connection.dbConfig.db.run {
      getUserChatsQuery(userId).result
    }
  }

  override def getMessagesAfterTimestamp(userId: Long, timestamp: Instant): Future[Seq[Message]] =
    Connection.dbConfig.db.run {
      getUserChatsQuery(userId)
        .join(messageRepo.messageTable)
        .on(_.id === _.chatId)
        .filter(_._2.timestamp >= timestamp)
        .map(_._2)
        .result
    }
}
