package com.telegraft.database.query

import com.telegraft.database.entity.UserTable.{userTable => users}
import com.telegraft.database.model.User
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

trait UserQuery {

  val db: Database

  def createUser(r: User): Future[Int] = db.run {
    users += r
  }

  def deleteUser(r: User): Future[Int] = db.run {
    val q = users.filter(_.id === r.id)
    q.delete
  }

  def getUser: Future[Seq[User]] = db.run {
    users.result
  }

  def getUserById(userId: Long): Future[User] = db.run {
    users.filter(_.id === userId).result.head
  }

}