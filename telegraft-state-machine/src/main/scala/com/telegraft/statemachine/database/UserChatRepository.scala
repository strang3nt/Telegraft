package com.telegraft.statemachine.database

import akka.Done
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

final case class UserChat(userId: Long, chatId: Long)

class UserChatRepository(val dbConfig: DatabaseConfig[PostgresProfile], val chats: ChatRepository, val users: UserRepository)(implicit ec: ExecutionContext) {

  private[database] class UserChatTable(tag: Tag) extends Table[UserChat](tag, "customer_chat") {

    def pk = primaryKey("customer_id_chat_id", (userId, chatId))
    def userId = column[Long]("customer_id")
    def chatId = column[Long]("chat_id")
    def user = foreignKey("customer_id_fk", userId, users.userTable)(_.id, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    def chat = foreignKey("chat_id_fk", chatId, chats.chatTable)(_.id, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    override def * = (userId, chatId) <> (UserChat.tupled, UserChat.unapply)
  }

  private[database] lazy val userChatTable = TableQuery[UserChatTable]

  def createUserChat(r: UserChat): DBIO[Done] = (userChatTable += r).map (_ => Done)

  def deleteUserChat(r: UserChat): DBIO[Done] = {
    val q = userChatTable.filter(e => e.chatId === r.chatId && e.userId === r.userId)
    q.delete.map(_ => Done)
  }

  def getUserChat: DBIO[Seq[UserChat]] = userChatTable.result

  def createTable: Future[Unit] = dbConfig.db.run {
    userChatTable.schema.createIfNotExists
  }
}