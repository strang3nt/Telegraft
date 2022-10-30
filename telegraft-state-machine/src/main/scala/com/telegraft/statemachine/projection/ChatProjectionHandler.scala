package com.telegraft.statemachine.projection

import akka.Done
import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.SlickHandler
import com.telegraft.statemachine.database.DatabaseRepository
import com.telegraft.statemachine.persistence.PersistentChat
import com.telegraft.statemachine.persistence.PersistentChat.{
  ChatCreated,
  ChatJoined,
  MessageAdded
}
import org.slf4j.LoggerFactory
import slick.dbio.DBIO

class ChatProjectionHandler()
    extends SlickHandler[EventEnvelope[PersistentChat.Event]] {

  private val logger = LoggerFactory.getLogger(getClass)

  override def process(
      envelope: EventEnvelope[PersistentChat.Event]): DBIO[Done] = {
    envelope.event match {
      case MessageAdded(msg) =>
        DatabaseRepository.messageRepo.createMessage(
          msg.userId,
          msg.chatId,
          msg.content,
          msg.timestamp)
      case ChatCreated(chat, userId) =>
        DatabaseRepository.createChatWithUser(chat.id, chat.name, userId)
      case ChatJoined(chatId, userId) =>
        DatabaseRepository.userChatRepo.createUserChat(userId, chatId)
    }
  }
}
