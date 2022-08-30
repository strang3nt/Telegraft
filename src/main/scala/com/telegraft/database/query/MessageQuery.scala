package com.telegraft.database.query

import com.telegraft.database.Connection
import com.telegraft.database.entity.MessageTable.{messageTable => messages}
import com.telegraft.model.Message
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

object MessageQuery extends Connection {

  def create(r: Message): Future[Int] = db.run { messages += r }

  def delete(r: Message): Future[Int] = db.run {
    val q = messages.filter(_.id === r.id)
    q.delete
  }

  def get: Future[Seq[Message]] = db.run { messages.result }

}