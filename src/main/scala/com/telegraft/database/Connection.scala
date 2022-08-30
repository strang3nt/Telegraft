package com.telegraft.database

import slick.jdbc.PostgresProfile.api._

trait Connection {
  val db = Database.forConfig("slick-postgres")
}
