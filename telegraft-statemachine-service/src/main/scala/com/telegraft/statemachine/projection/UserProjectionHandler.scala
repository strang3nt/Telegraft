package com.telegraft.statemachine.projection

import akka.Done
import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.SlickHandler
import com.telegraft.statemachine.database.DatabaseRepository
import com.telegraft.statemachine.persistence.PersistentUser
import com.telegraft.statemachine.persistence.PersistentUser.UserCreated
import org.slf4j.LoggerFactory
import slick.dbio.DBIO

class UserProjectionHandler(repository: DatabaseRepository)
    extends SlickHandler[EventEnvelope[PersistentUser.Event]] {

  override def process(
      envelope: EventEnvelope[PersistentUser.Event]): DBIO[Done] = {
    envelope.event match {
      case UserCreated(userId, userName) =>
        repository.userRepo.createUser(userId, userName)
    }
  }
}
