package com.telegraft.database.query

import com.telegraft.database.Connection
import com.telegraft.database.entity.MessageTable.{messageTable => messages}
import com.telegraft.database.model.Message
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

trait MessageQuery {

  val db: Database

  def createMessage(r: Message): Future[Int] = db.run {
    messages += r
  }

  def deleteMessage(r: Message): Future[Int] = db.run {
    val q = messages.filter(_.id === r.id)
    q.delete
  }

  def getMessage: Future[Seq[Message]] = db.run {
    messages.result
  }
}