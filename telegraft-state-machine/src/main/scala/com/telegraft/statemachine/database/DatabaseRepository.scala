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

  def createUserChat(userId: String, chatId: String): DBIO[Done] = ???
  def getUserChats(userId: String): Future[Seq[Chat]] = {

    dbConfig.db.run {
      userChatRepo.userChatTable
        .filter(_.userId === userId)
        .join(chatRepo.chatTable)
        .on(_.userId === _.id)
        .map(_._2)
        .result
    }
  }

  def getMessagesAfterTimestamp(
      userId: String,
      timestamp: Instant): Future[Seq[Message]] = ???
}
