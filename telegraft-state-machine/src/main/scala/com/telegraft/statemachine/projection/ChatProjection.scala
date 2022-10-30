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
import akka.projection.{ProjectionBehavior, ProjectionId}
import com.telegraft.statemachine.database.Connection
import com.telegraft.statemachine.persistence.PersistentChat

object ChatProjection {

  def init(system: ActorSystem[_]): Unit = {
    ShardedDaemonProcess(system).init(
      name = "PersistentChatProjection",
      PersistentChat.tags.size,
      index =>
        ProjectionBehavior(createProjectionFor(system, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }

  private def createProjectionFor(
                                   system: ActorSystem[_],
                                   index: Int)
  : ExactlyOnceProjection[Offset, EventEnvelope[PersistentChat.Event]] = {

    implicit val sys: ActorSystem[_] = system
    val tag = PersistentChat.tags(index)

    val sourceProvider =
      EventSourcedProvider.eventsByTag[PersistentChat.Event](
        system = system,
        readJournalPluginId = JdbcReadJournal.Identifier,
        tag = tag)

    SlickProjection.exactlyOnce(
      projectionId = ProjectionId("PersistentChatProjection", tag),
      sourceProvider,
      Connection.dbConfig,
      handler = () => new ChatProjectionHandler())

  }

}
