package com.telegraft.database.query

import com.telegraft.database.Connection
import com.telegraft.database.entity.UserChatTable.{userChatTable => usersChats}
import com.telegraft.model.UserChat
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

object UserChatQuery extends Connection {

  def create(r: UserChat): Future[Int] = db.run {
    usersChats += r
  }

  def delete(r: UserChat): Future[Int] = db.run {
    val q = usersChats.filter(e => e.chatId === r.chatId && e.userId === r.userId)
    q.delete
  }

  def get: Future[Seq[UserChat]] = db.run {
    usersChats.result
  }

}