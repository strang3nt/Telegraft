package com.telegraft.database.query

import com.telegraft.database.Connection
import com.telegraft.database.entity.UserTable.{userTable => users}
import com.telegraft.model.User
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

object UserQuery extends Connection {

  def create(r: User): Future[Int] = db.run { users += r }

  def delete(r: User): Future[Int] = db.run {
    val q = users.filter(_.id === r.id)
    q.delete
  }

  def get: Future[Seq[User]] = db.run { users.result }

}