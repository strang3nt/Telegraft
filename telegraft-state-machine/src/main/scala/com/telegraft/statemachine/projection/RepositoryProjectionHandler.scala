package com.telegraft.statemachine.projection

import akka.Done
import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.SlickHandler
import com.telegraft.statemachine.database.UserRepository
import com.telegraft.statemachine.persistence.PersistentUser
import com.telegraft.statemachine.persistence.PersistentUser.UserCreated
import com.telegraft.statemachine.database.User
import org.slf4j.LoggerFactory
import slick.dbio.DBIO

class RepositoryProjectionHandler(userRepository: UserRepository)
    extends SlickHandler[EventEnvelope[PersistentUser.Event]] {

  private val logger = LoggerFactory.getLogger(getClass)

  override def process(
      envelope: EventEnvelope[PersistentUser.Event]): DBIO[Done] = {
    envelope.event match {
      case UserCreated(userId, userName) =>
        logger.info(s"User ${userName} is added to database")
        userRepository.createUser(User(userId, userName))

      case otherEvent =>
        DBIO.successful(Done)
    }
  }
}
