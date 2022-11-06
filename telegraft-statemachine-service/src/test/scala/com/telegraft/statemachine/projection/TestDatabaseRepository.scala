package com.telegraft.statemachine.projection

import akka.Done
import com.telegraft.statemachine.database
import com.telegraft.statemachine.database.{
  ChatRepository,
  Connection,
  DatabaseRepository,
  Message,
  MessageRepository,
  UserChat,
  UserChatRepository,
  UserRepository
}
import com.telegraft.statemachine.projection.TestDatabaseRepository.{
  TestChatRepository,
  TestMessageRepository,
  TestUserChatRepository,
  TestUserRepository
}
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc
import slick.jdbc.PostgresProfile

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

object TestDatabaseRepository {

  def init: TestDatabaseRepository = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val users = new TestUserRepository(Connection.dbConfig)
    val chats = new TestChatRepository(Connection.dbConfig)
    val userChats =
      new TestUserChatRepository(Connection.dbConfig, users, chats)
    val messages = new TestMessageRepository(Connection.dbConfig, users, chats)

    new TestDatabaseRepository(messages, userChats, users, chats)
  }

  class TestMessageRepository(
      override val dbConfig: DatabaseConfig[PostgresProfile],
      override val users: UserRepository,
      override val chats: ChatRepository)(implicit ex: ExecutionContext)
      extends MessageRepository(dbConfig, chats, users) {

    var messages = List.empty[Message]
    private var lastId: Int = -1

    override def createMessage(
        userId: String,
        chatId: String,
        content: String,
        timestamp: Instant): jdbc.PostgresProfile.api.DBIO[Done] = {
      lastId += 1
      messages :+= database.Message(lastId, userId, chatId, content, timestamp)
      DBIO.successful(Done)
    }
  }

  class TestUserChatRepository(
      override val dbConfig: DatabaseConfig[PostgresProfile],
      override val users: UserRepository,
      override val chats: ChatRepository)(implicit ex: ExecutionContext)
      extends UserChatRepository(dbConfig, chats, users) {

    var usersChats = List.empty[UserChat]

    override def createUserChat(
        userId: String,
        chatId: String): _root_.slick.jdbc.PostgresProfile.api.DBIO[Done] = {
      usersChats :+= database.UserChat(userId, chatId)
      DBIO.successful(Done)
    }
  }

  class TestUserRepository(
      override val dbConfig: DatabaseConfig[PostgresProfile])(
      implicit ex: ExecutionContext)
      extends UserRepository(dbConfig) {

    var users = Map.empty[String, String]

    override def createUser(userId: String, userName: String): DBIO[Done] = {
      users += userId -> userName
      DBIO.successful(Done)
    }
  }

  class TestChatRepository(
      override val dbConfig: DatabaseConfig[PostgresProfile])(
      implicit ex: ExecutionContext)
      extends ChatRepository(dbConfig) {

    var chats = List.empty[database.Chat]

    override def createChat(
        r: database.Chat): _root_.slick.jdbc.PostgresProfile.api.DBIO[Done] = {
      chats :+= r
      DBIO.successful(Done)
    }
  }

}

class TestDatabaseRepository(
    override val messageRepo: TestMessageRepository,
    override val userChatRepo: TestUserChatRepository,
    override val userRepo: TestUserRepository,
    override val chatRepo: TestChatRepository)(implicit ex: ExecutionContext)
    extends DatabaseRepository {

  override def createTables: Future[Unit] = Future.unit

  override def createChatWithUser(
      chatId: String,
      chatName: String,
      userId: String): _root_.slick.jdbc.PostgresProfile.api.DBIO[Done] = {

    chatRepo.createChat(database.Chat(chatId, chatName))
    userChatRepo.createUserChat(userId, chatId)

    DBIO.successful(Done)
  }

  override def getUserChats(userId: String): Future[Seq[database.Chat]] =
    Future.successful(Seq.empty[database.Chat])

  override def getMessagesAfterTimestamp(
      userId: String,
      timestamp: Instant): Future[Seq[Message]] = Future(Seq.empty[Message])
}
