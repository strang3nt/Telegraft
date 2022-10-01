package com.telegraft.projection

import akka.Done
import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.SlickHandler
import com.telegraft.database.{User, UserRepository}
import com.telegraft.persistence.PersistentRepository
import com.telegraft.persistence.PersistentRepository.UserCreated
import org.slf4j.LoggerFactory
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext

class RepositoryProjectionHandler(val userRepository: UserRepository)(implicit ec: ExecutionContext)
    extends SlickHandler[EventEnvelope[PersistentRepository.Event]] {

  private val logger = LoggerFactory.getLogger(getClass)

  override def process(envelope: EventEnvelope[PersistentRepository.Event]): DBIO[Done] = {
    envelope.event match {
      case UserCreated(user) =>
        logger.info(s"User ${user.username} is added to database")
        userRepository.createUser(User(0, user.username))

      case otherEvent =>
        DBIO.successful(Done)
    } 
  }
}