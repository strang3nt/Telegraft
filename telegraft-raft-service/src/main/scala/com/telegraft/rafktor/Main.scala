package com.telegraft.rafktor

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.grpc.GrpcClientSettings
import com.telegraft.statemachine.proto.TelegraftStateMachineServiceClient
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

object Main {

  private val logger = LoggerFactory.getLogger("com.telegraft.rafktor.Main")

  def main(args: Array[String]): Unit = {
    ActorSystem[Nothing](
      Behaviors.setup[Nothing] { ctx =>
        try {
          init(ctx)
          Behaviors.empty
        } catch {
          case NonFatal(e) =>
            logger.error("Terminating due to initialization failure.", e)
            ctx.system.terminate()
            Behaviors.empty
        }
      },
      "TelegraftRaftService")
  }

  def init(context: ActorContext[_]): Unit = {

    implicit val system: ActorSystem[Nothing] = context.system

    val grpcClient =
      GrpcClientSettings
        .connectToServiceAt(
          system.settings.config.getString("telegraft-statemachine-service.host"),
          system.settings.config.getInt("telegraft-statemachine-service.port"))
        // Tls set to false means no encryption over HTTP/2
        .withTls(false)

    val telegraftGrpcClient: TelegraftStateMachineServiceClient =
      TelegraftStateMachineServiceClient(grpcClient)

    val config = Configuration(system)
    logger.info("Using the following configuration: " + config.getConfiguration.map(_.id))

    val stateMachine = context.spawn(StateMachine(telegraftGrpcClient), "StateMachine")

    val persistentId =
      system.settings.config.getString("telegraft-raft-service.grpc.interface") + ":" + system.settings.config
        .getString("telegraft-raft-service.grpc.port")
    val raftNode = context.spawnAnonymous(RaftServer.apply(persistentId, stateMachine, config))

    val grpcInterface =
      system.settings.config.getString("telegraft-raft-service.grpc.interface")
    val grpcPort =
      system.settings.config.getInt("telegraft-raft-service.grpc.port")
    val raftService = new RaftServiceImpl(raftNode)
    val raftClientService = new RaftClientServiceImpl(raftNode, stateMachine)
    TelegraftRaftServer.start(grpcInterface, grpcPort, system, raftService, raftClientService)
  }

}
