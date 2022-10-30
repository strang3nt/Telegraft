package com.telegraft.statemachine.projection

import akka.Done
import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.SlickHandler
import com.telegraft.statemachine.database.{ DatabaseRepository, User }
import com.telegraft.statemachine.persistence.PersistentUser
import com.telegraft.statemachine.persistence.PersistentUser.UserCreated
import org.slf4j.LoggerFactory
import slick.dbio.DBIO

class UserProjectionHandler()
    extends SlickHandler[EventEnvelope[PersistentUser.Event]] {

  private val logger = LoggerFactory.getLogger(getClass)

  override def process(
      envelope: EventEnvelope[PersistentUser.Event]): DBIO[Done] = {
    envelope.event match {
      case UserCreated(userId, userName) =>
        DatabaseRepository.userRepo.createUser(userId, userName)
    }
  }
}
