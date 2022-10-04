package com.telegraft.projection

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorSystem
import akka.persistence.query.Offset
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.ExactlyOnceProjection
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.projection.slick.SlickProjection
import com.telegraft.database.{Connection, UserRepository}
import com.telegraft.persistence.PersistentRepository

import scala.concurrent.ExecutionContext

object RepositoryProjection {

  def init(ctx: ActorContext[_], repository: UserRepository): Unit = {
    val projection1 = createProjectionFor(ctx.system, repository)
    ctx.spawn(ProjectionBehavior(projection1), projection1.projectionId.id)
  }

  private def createProjectionFor(system: ActorSystem[_], repository: UserRepository):
    ExactlyOnceProjection[Offset, EventEnvelope[PersistentRepository.Event]] = {

    implicit val ec = system.executionContext
    implicit val sys = system

    val tag = PersistentRepository.tags.head

    val sourceProvider =
      EventSourcedProvider
        .eventsByTag[PersistentRepository.Event](
          system = system,
          readJournalPluginId = JdbcReadJournal.Identifier,
          tag = tag)

    SlickProjection.exactlyOnce(
      projectionId = ProjectionId("users", tag),
      sourceProvider,
      Connection.dbConfig,
      handler = () => new RepositoryProjectionHandler(repository))

  }

}
