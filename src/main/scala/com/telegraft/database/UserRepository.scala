package com.telegraft.database

import akka.Done
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

final case class User(id: Long, userName: String)

class UserRepository(val dbConfig: DatabaseConfig[PostgresProfile])(implicit ec: ExecutionContext) {

  private[database] class UserTable(tag: Tag) extends Table[User](tag, "customer") {

    override def * = (id, userName) <> (User.tupled, User.unapply)
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userName = column[String]("username", O.Unique)
  }

  private[database] lazy val userTable = TableQuery[UserTable]

  def createUser(r: User): DBIO[Done] = (userTable += r).map(_ => Done)

  def deleteUser(r: User): DBIO[Done] = {
    val q = userTable.filter(_.id === r.id)
    q.delete.map(_ => Done)
  }

  def getUsers: DBIO[Seq[User]] = userTable.result

  def getUserById(userId: Long): Future[User] = dbConfig.db.run {
    userTable.filter(_.id === userId).result.head
  }

  def createTable: Future[Unit] = dbConfig.db.run {
    userTable.schema.createIfNotExists
  }
}