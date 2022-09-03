package com.telegraft.database.query

import com.telegraft.database.Connection
import com.telegraft.database.entity.ChatTable.{chatTable => chats}
import com.telegraft.model.Chat
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

object ChatQuery extends Connection {

  def create(r: Chat): Future[Int] = db.run {
    chats += r
  }

  def delete(r: Chat): Future[Int] = db.run {
    val q = chats.filter(_.id === r.id)
    q.delete
  }

  def get: Future[Seq[Chat]] = db.run {
    chats.result
  }

}