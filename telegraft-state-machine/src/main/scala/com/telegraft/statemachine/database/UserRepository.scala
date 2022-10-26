package com.telegraft.statemachine.database

import akka.Done
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ ExecutionContext, Future }

final case class User(id: String, userName: String)

class UserRepository(val dbConfig: DatabaseConfig[PostgresProfile])(
    implicit ec: ExecutionContext) {

  private[database] class UserTable(tag: Tag)
      extends Table[User](tag, "customer") {
    def id = column[String]("id", O.PrimaryKey)
    def userName = column[String]("username", O.Unique)
    override def * = (id, userName).mapTo[User]
  }

  private[database] lazy val userTable = TableQuery[UserTable]

  def createUser(r: User): DBIO[Done] = (userTable += r).map(_ => Done)

  def deleteUser(r: User): DBIO[Done] = {
    val q = userTable.filter(_.id === r.id)
    q.delete.map(_ => Done)
  }

  def getUsers: DBIO[Seq[User]] = userTable.result

  def getUserById(userId: String): Future[User] = dbConfig.db.run {
    userTable.filter(_.id === userId).result.head
  }

  def createTable: Future[Unit] = dbConfig.db.run {
    userTable.schema.createIfNotExists
  }
}
