package com.telegraft.statemachine.database

import akka.Done
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

final case class Message(id: Long, userId: Long, chatId: Long, content: String, timestamp: Instant)

class MessageRepository(
    val dbConfig: DatabaseConfig[PostgresProfile],
    val chats: ChatRepository,
    val users: UserRepository)(implicit ec: ExecutionContext) {

  private[database] class MessageTable(tag: Tag) extends Table[Message](tag, "message") {

    def timestamp = column[Instant]("sent_time", O.Default(Instant.now()))

    def userId = column[Long]("customer_id")
    def chatId = column[Long]("chat_id")

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def content = column[String]("content")
    def chat = foreignKey("chat_id_fk", chatId, chats.chatTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade)

    def user = foreignKey("customer_id_fk", userId, users.userTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade)

    override def * =
      (id, userId, chatId, content, timestamp).mapTo[Message]

  }

  def createMessage(userId: Long, chatId: Long, content: String, timestamp: Instant): Future[Long] = dbConfig.db.run {
    messageTable.returning(messageTable.map(_.id)) += Message(0, userId, chatId, content, timestamp)
  }
  def deleteMessage(messageId: Long): DBIO[Done] = {
    val q = messageTable.filter(_.id === messageId)
    q.delete.map(_ => Done)
  }

  private[database] lazy val messageTable = TableQuery[MessageTable]

  def getMessage: DBIO[Seq[Message]] = messageTable.result

  def createTable: Future[Unit] = dbConfig.db.run {
    messageTable.schema.createIfNotExists
  }
}
