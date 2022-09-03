package com.telegraft.database.entity

import com.telegraft.model.Chat
import slick.jdbc.PostgresProfile.api._

class ChatTable(tag: Tag) extends Table[Chat](tag, "Chat") {
  override def * = (id, chatName) <> (Chat.tupled, Chat.unapply)

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def chatName = column[String]("username")
}

object ChatTable {
  lazy val chatTable = TableQuery[ChatTable]
}
