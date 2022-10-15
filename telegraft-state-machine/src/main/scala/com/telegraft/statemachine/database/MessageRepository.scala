package com.telegraft.statemachine.database

import akka.Done
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

final case class Message(id: Long, userId: Long, chatId: Long, content: String)

class MessageRepository(val dbConfig: DatabaseConfig[PostgresProfile], val chats: ChatRepository, val users: UserRepository)(implicit ec: ExecutionContext) {

  private[database] class MessageTable(tag: Tag) extends Table[Message](tag, "message") {

    def timestamp = column[Instant]("sent_time", O.Default(Instant.now()))
    def user = foreignKey("customer_id_fk", userId, users.userTable)(_.id, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    def userId = column[Long]("customer_id")
    def chat = foreignKey("chat_id_fk", chatId, chats.chatTable)(_.id, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    def chatId = column[Long]("chat_id")
    override def * = (id, userId, chatId, content) <> (Message.tupled, Message.unapply)
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def content = column[String]("content")
  }

  def createMessage(r: Message): DBIO[Done] = (messageTable += r).map(_ => Done)

  def deleteMessage(r: Message): DBIO[Done] = {
    val q = messageTable.filter(_.id === r.id)
    q.delete.map(_ => Done)
  }

  private[database] lazy val messageTable = TableQuery[MessageTable]

  def getMessage: DBIO[Seq[Message]] = messageTable.result

  def createTable: Future[Unit] = dbConfig.db.run {
    messageTable.schema.createIfNotExists
  }
}