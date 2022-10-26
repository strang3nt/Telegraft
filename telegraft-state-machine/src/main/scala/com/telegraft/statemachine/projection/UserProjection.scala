package com.telegraft.statemachine.projection

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.ExactlyOnceProjection
import akka.projection.slick.SlickProjection
import akka.projection.{ ProjectionBehavior, ProjectionId }
import com.telegraft.statemachine.database.{ Connection, UserRepository }
import com.telegraft.statemachine.persistence.PersistentUser

object UserProjection {

  def init(system: ActorSystem[_], repository: UserRepository): Unit = {
    ShardedDaemonProcess(system).init(
      name = "PersistentUserProjection",
      PersistentUser.tags.size,
      index =>
        ProjectionBehavior(createProjectionFor(system, repository, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }

  private def createProjectionFor(
      system: ActorSystem[_],
      repository: UserRepository,
      index: Int)
      : ExactlyOnceProjection[Offset, EventEnvelope[PersistentUser.Event]] = {

    implicit val sys = system
    val tag = PersistentUser.tags(index)

    val sourceProvider =
      EventSourcedProvider.eventsByTag[PersistentUser.Event](
        system = system,
        readJournalPluginId = JdbcReadJournal.Identifier,
        tag = tag)

    SlickProjection.exactlyOnce(
      projectionId = ProjectionId("PersistentUserProjection", tag),
      sourceProvider,
      Connection.dbConfig,
      handler = () => new RepositoryProjectionHandler(repository))

  }

}
