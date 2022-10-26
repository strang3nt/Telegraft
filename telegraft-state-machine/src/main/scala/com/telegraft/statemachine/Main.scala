package com.telegraft.statemachine

import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import com.telegraft.statemachine.database.{ Connection, UserRepository }
import com.telegraft.statemachine.persistence.PersistentUser
import com.telegraft.statemachine.projection.UserProjection
import scala.concurrent.ExecutionContextExecutor
import akka.management.scaladsl.AkkaManagement
import org.slf4j.LoggerFactory
import scala.util.control.NonFatal

object Main {

  val logger = LoggerFactory.getLogger("com.telegraft.statemachine.Main")

  def main(args: Array[String]): Unit = {
    ActorSystem[Nothing](
      Behaviors.setup[Nothing] { context =>
        implicit val system: ActorSystem[Nothing] = context.system
        implicit val ec: ExecutionContextExecutor =
          context.system.executionContext

        val persistentRepository =
          context.spawn(PersistentUser(), "PersistentRepository")

        UserProjection
          .init(context.system, new UserRepository(Connection.dbConfig))

        val stateMachine =
          context.spawn(StateMachine(persistentRepository), "StateMachine")

        try {
          init(stateMachine, system)
        } catch {
          case NonFatal(e) =>
            logger.error("Terminating due to initialization failure.", e)
            system.terminate()
        }
        Behaviors.empty
      },
      "TelegraftStateMachineService")

  }

  def init(
      stateMachine: ActorRef[StateMachine.Command],
      system: ActorSystem[_]): Unit = {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    val grpcInterface =
      system.settings.config
        .getString("telegraft-statemachine-service.grpc.interface")
    val grpcPort =
      system.settings.config.getInt("telegraft-statemachine-service.grpc.port")
    val grpcService = new TelegraftStateMachineImpl(stateMachine)(system)
    TelegraftStateMachineServer.start(
      grpcInterface,
      grpcPort,
      system,
      grpcService)
  }

}
