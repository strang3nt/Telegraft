package com.telegraft.database.entity

import com.telegraft.database.model.Message
import slick.jdbc.PostgresProfile.api._

import java.time.Instant

class MessageTable(tag: Tag) extends Table[Message](tag, "message") {
  def timestamp = column[Instant]("sent_time", O.Default(Instant.now()))

  def user = foreignKey("customer_id_fk", userId, UserTable.userTable)(_.id, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)

  def userId = column[Long]("customer_id")

  def chat = foreignKey("chat_id_fk", chatId, ChatTable.chatTable)(_.id, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)

  def chatId = column[Long]("chat_id")

  override def * = (id, userId, chatId, content) <> (Message.tupled, Message.unapply)

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def content = column[String]("content")
}

object MessageTable {
  lazy val messageTable = TableQuery[MessageTable]
}