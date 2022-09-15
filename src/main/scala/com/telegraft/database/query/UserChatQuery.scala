package com.telegraft.database.query

import com.telegraft.database.Connection
import com.telegraft.database.entity.UserChatTable.{userChatTable => usersChats}
import com.telegraft.database.model.UserChat
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

trait UserChatQuery {

  val db: Database

  def createUserChat(r: UserChat): Future[Int] = db.run {
    usersChats += r
  }

  def deleteUserChat(r: UserChat): Future[Int] = db.run {
    val q = usersChats.filter(e => e.chatId === r.chatId && e.userId === r.userId)
    q.delete
  }

  def getUserChat: Future[Seq[UserChat]] = db.run {
    usersChats.result
  }
}
