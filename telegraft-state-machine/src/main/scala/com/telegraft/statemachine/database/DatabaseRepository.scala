package com.telegraft.statemachine.database

import akka.Done
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

class DatabaseRepository (
                           dbConfig: DatabaseConfig[PostgresProfile],
                           chatRepository: ChatRepository,
                           userRepository: UserRepository,
                           messageRepository: MessageRepository,
                           userChatRepository: UserChatRepository) {

  def createTable: Future[Unit] = dbConfig.db.run{
    DBIO.seq(
      chatRepository.chatTable.schema.createIfNotExists,
      userRepository.userTable.schema.createIfNotExists,
      messageRepository.messageTable.schema.createIfNotExists,
      userChatRepository.userChatTable.schema.createIfNotExists
    ).transactionally
  }

  def createUserChat(userId: Long, chat: Chat): DBIO[Done] = ???

}