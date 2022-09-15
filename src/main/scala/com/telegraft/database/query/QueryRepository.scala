package com.telegraft.database.query

import com.telegraft.database.Connection
import com.telegraft.database.entity.ChatTable.{chatTable => chats}
import com.telegraft.database.entity.UserChatTable.{userChatTable => usersChats}
import com.telegraft.database.model.{Chat, User, UserChat}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

object QueryRepository {
  def apply(db: Database) = new QueryRepository(db)
}
class QueryRepository (database: Database)
  extends ChatQuery
  with MessageQuery
  with UserChatQuery
  with UserQuery {

  override val db = database

  def createUserChat(userId: Long, chat: Chat): Future[Int] = db.run {
    (chats returning chats.map(_.id) += chat)
      .flatMap(usersChats += UserChat(userId, _))
      .transactionally
  }

}
