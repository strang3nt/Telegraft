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
import slick.dbio.DBIO

class ChatProjectionHandler(repository: DatabaseRepository)
    extends SlickHandler[EventEnvelope[PersistentChat.Event]] {

  override def process(
      envelope: EventEnvelope[PersistentChat.Event]): DBIO[Done] = {
    envelope.event match {
      case MessageAdded(msg) =>
        repository.messageRepo.createMessage(
          msg.userId,
          msg.chatId,
          msg.content,
          msg.timestamp)
      case ChatCreated(chat, userId) =>
        repository.createChatWithUser(chat.id, chat.name, userId)
      case ChatJoined(chatId, userId) =>
        repository.userChatRepo.createUserChat(userId, chatId)
    }
  }
}
