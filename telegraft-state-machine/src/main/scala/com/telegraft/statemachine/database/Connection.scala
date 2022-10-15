package com.telegraft.statemachine.database

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

object Connection {
  val dbConfig = DatabaseConfig.forConfig[PostgresProfile]("akka-persistence-jdbc.shared-databases.slick")

}
