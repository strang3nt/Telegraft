package com.telegraft.database.entity

import slick.jdbc.PostgresProfile.api._
import java.time.Instant
import com.telegraft.model.Message

class MessageTable(tag: Tag) extends Table[Message](tag, "Message") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def chatId = column[Long]("chat_id")
    def content = column[String]("content")
    def timestamp = column[Instant]("timestamp", O.Default(Instant.now()))
    
    def user = foreignKey("user_id_fk", userId, UserTable.userTable)(_.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)
    def chat = foreignKey("chat_id_fk", chatId, ChatTable.chatTable)(_.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)
    override def * = (id, userId, chatId, content) <> (Message.tupled, Message.unapply)
}
object MessageTable {
  lazy val messageTable = TableQuery[MessageTable]
}