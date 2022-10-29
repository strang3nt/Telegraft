package com.telegraft.statemachine

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import com.telegraft.statemachine.database.DatabaseRepository
import com.telegraft.statemachine.persistence.{ PersistentChat, PersistentUser }
import com.telegraft.statemachine.projection.UserProjection
import akka.management.scaladsl.AkkaManagement
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

object Main {

  val logger = LoggerFactory.getLogger("com.telegraft.statemachine.Main")

  def main(args: Array[String]): Unit = {

    val system =
      ActorSystem[Nothing](Behaviors.empty, "TelegraftStateMachineService")
    try {
      init(system)
    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(system: ActorSystem[_]): Unit = {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    DatabaseRepository.createTable

    PersistentUser.init(system)
    PersistentChat.init(system)
    UserProjection.init(system)

    val grpcInterface =
      system.settings.config
        .getString("telegraft-statemachine-service.grpc.interface")
    val grpcPort =
      system.settings.config.getInt("telegraft-statemachine-service.grpc.port")
    val grpcService = new TelegraftStateMachineImpl(system)
    TelegraftStateMachineServer.start(
      grpcInterface,
      grpcPort,
      system,
      grpcService)
  }

}
