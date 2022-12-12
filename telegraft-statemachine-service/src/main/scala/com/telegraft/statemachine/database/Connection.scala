package com.telegraft.statemachine.database

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

object Connection {
  val dbConfig = DatabaseConfig.forConfig[PostgresProfile]("telegraft-statemachine-service.slick")

}
