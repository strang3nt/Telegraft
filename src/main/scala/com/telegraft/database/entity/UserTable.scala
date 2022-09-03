package com.telegraft.database.entity

import com.telegraft.model.User
import slick.jdbc.PostgresProfile.api._

class UserTable(tag: Tag) extends Table[User](tag, "User") {
  override def * = (id, userName, password) <> (User.tupled, User.unapply)

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def userName = column[String]("username", O.Unique)

  def password = column[String]("password")
}

object UserTable {
  lazy val userTable = TableQuery[UserTable]
}
