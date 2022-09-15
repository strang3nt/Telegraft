package com.telegraft.database.query

import com.telegraft.database.entity.ChatTable.{chatTable => chats}
import com.telegraft.database.model.Chat
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

trait ChatQuery {

  val db: Database

  def createChat(r: Chat): Future[Int] = db.run {
    chats += r
  }

  def deleteChat(r: Chat): Future[Int] = db.run {
    val q = chats.filter(_.id === r.id)
    q.delete
  }

  def getChat: Future[Seq[Chat]] = db.run {
    chats.result
  }
}