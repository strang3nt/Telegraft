package com.telegraft.database.entity

import com.telegraft.model.UserChat
import slick.jdbc.PostgresProfile.api._

class UserChatTable(tag: Tag) extends Table[UserChat](tag, "User_Chat") {
    def userId = column[Long]("user_id")
    def chatId = column[Long]("chat_id")

    def pk = primaryKey("user_id_chat_id", (userId, chatId))
    def user = foreignKey("user_id_fk", userId, UserTable.userTable)(_.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)
    def chat = foreignKey("chat_id_fk", chatId, ChatTable.chatTable)(_.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)
    
    override def * = (userId, chatId) <> (UserChat.tupled, UserChat.unapply)
}
object UserChatTable {
  lazy val userChatTable = TableQuery[UserChatTable]
}