package com.telegraft.database.entity

import com.telegraft.database.model.UserChat
import slick.jdbc.PostgresProfile.api._

class UserChatTable(tag: Tag) extends Table[UserChat](tag, "customer_chat") {
  def pk = primaryKey("customer_id_chat_id", (userId, chatId))

  def userId = column[Long]("customer_id")

  def chatId = column[Long]("chat_id")

  def user = foreignKey("customer_id_fk", userId, UserTable.userTable)(_.id, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)

  def chat = foreignKey("chat_id_fk", chatId, ChatTable.chatTable)(_.id, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)

  override def * = (userId, chatId) <> (UserChat.tupled, UserChat.unapply)
}

object UserChatTable {
  lazy val userChatTable = TableQuery[UserChatTable]
}