package com.telegraft.database.entity

import com.telegraft.database.model.Chat
import slick.jdbc.PostgresProfile.api._

class ChatTable(tag: Tag) extends Table[Chat](tag, "chat") {
  override def * = (id, chatName) <> (Chat.tupled, Chat.unapply)

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def chatName = column[String]("chatname")
}

object ChatTable {
  lazy val chatTable = TableQuery[ChatTable]
}
