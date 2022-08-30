package com.telegraft.database.entity

import com.telegraft.model.Chat
import slick.jdbc.PostgresProfile.api._

class ChatTable(tag: Tag) extends Table[Chat](tag, "Chat") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def chatName = column[String]("username")
    override def * = (id, chatName) <> (Chat.tupled, Chat.unapply)
}
object ChatTable {
  lazy val chatTable = TableQuery[ChatTable]
}
