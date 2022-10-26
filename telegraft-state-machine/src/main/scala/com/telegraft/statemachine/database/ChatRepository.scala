package com.telegraft.statemachine.database

import akka.Done
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ ExecutionContext, Future }

final case class Chat(id: String, chatName: String)

class ChatRepository(val dbConfig: DatabaseConfig[PostgresProfile])(
    implicit ec: ExecutionContext) {

  private[database] class ChatTable(tag: Tag) extends Table[Chat](tag, "chat") {

    override def * = (id, chatName).mapTo[Chat]
    def id = column[String]("chat_id", O.PrimaryKey)
    def chatName = column[String]("chatname")
  }

  private[database] lazy val chatTable = TableQuery[ChatTable]

  def createChat(r: Chat): DBIO[Done] = (chatTable += r).map(_ => Done)

  def deleteChat(r: Chat): DBIO[Done] = {
    val q = chatTable.filter(_.id === r.id)
    q.delete.map(_ => Done)
  }

  def getChats: DBIO[Seq[Chat]] = chatTable.result

  def createTable: Future[Unit] = dbConfig.db.run {
    chatTable.schema.createIfNotExists
  }
}
