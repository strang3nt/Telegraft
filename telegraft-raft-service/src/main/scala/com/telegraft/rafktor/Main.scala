package com.telegraft.rafktor

import akka.actor.typed.{ ActorSystem, MailboxSelector }
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.grpc.GrpcClientSettings
import com.telegraft.statemachine.proto.TelegraftStateMachineServiceClient

import scala.util.control.NonFatal

object Main {

  def main(args: Array[String]): Unit = {
    ActorSystem[Nothing](
      Behaviors.setup[Nothing] { ctx =>
        try {
          init(ctx)
          Behaviors.empty
        } catch {
          case NonFatal(e) =>
            ctx.system.log.error("Terminating due to initialization failure.", e)
            ctx.system.terminate()
            Behaviors.empty
        }
      },
      "TelegraftRaftService")
  }

  private def init(context: ActorContext[_]): Unit = {

    implicit val system: ActorSystem[Nothing] = context.system

    val telegraftGrpcClient: TelegraftStateMachineServiceClient =
      TelegraftStateMachineServiceClient(
        GrpcClientSettings
          .connectToServiceAt(
            system.settings.config.getString("telegraft-statemachine-service.host"),
            system.settings.config.getInt("telegraft-statemachine-service.port"))
          // Tls set to false means no encryption over HTTP/2
          .withTls(false))

    val config = Configuration(system)
    system.log.info("Using the following configuration: " + config.getConfiguration.map(_.id))

    val stateMachine = context.spawn(StateMachine(telegraftGrpcClient), "StateMachine")

    val persistentId =
      system.settings.config.getString("telegraft-raft-service.grpc.interface") + ":" + system.settings.config
        .getString("telegraft-raft-service.grpc.port")
    val props = MailboxSelector.fromConfig("telegraft-raft-service")
    val raftNode = context.spawnAnonymous(RaftServer.apply(persistentId, stateMachine, config))

    TelegraftRaftServer.start(
      system.settings.config.getString("telegraft-raft-service.grpc.interface"),
      system.settings.config.getInt("telegraft-raft-service.grpc.port"),
      system,
      new RaftServiceImpl(raftNode),
      new RaftClientServiceImpl(raftNode, stateMachine))
  }
}
