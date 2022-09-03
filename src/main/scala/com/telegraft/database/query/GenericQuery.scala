package com.telegraft.database.query

import com.telegraft.database.Connection
import com.telegraft.database.entity.ChatTable.{chatTable => chats}
import com.telegraft.database.entity.UserChatTable.{userChatTable => usersChats}
import com.telegraft.model.{Chat, User, UserChat}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

object GenericQuery extends Connection {

  def createUserChat(u: User, c: Chat): Future[Int] = db.run {
    (chats returning chats.map(_.id) += c)
      .flatMap(usersChats += UserChat(u.id, _))
      .transactionally
  }

}
