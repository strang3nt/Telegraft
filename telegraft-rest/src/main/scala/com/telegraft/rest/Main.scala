package com.telegraft.rest

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import org.slf4j.LoggerFactory
import scala.util.control.NonFatal
import akka.actor.typed.scaladsl.ActorContext

import com.telegraft.statemachine.proto.{ 
  TelegraftStateMachineService, 
  TelegraftStateMachineServiceClient }
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.Http
import scala.util.Success
import scala.util.Failure

object Main {

  val logger = LoggerFactory.getLogger("com.telegraft.rest.Main")

  def main(args: Array[String]): Unit = {
    ActorSystem[Nothing](Behaviors.setup[Nothing]{context =>
      try {
        val stateMachineService = stateMachineServiceClient(
          context.system,
          "telegraft-statemachine-service.host",
          "telegraft-statemachine-service.port")
      
        val raftService = stateMachineServiceClient(
          context.system,
          "telegraft-raft-service.host",
          "telegraft-raft-service.port"
        )
        init(context, stateMachineService, raftService)
        Behaviors.empty
      } catch {
        case NonFatal(e) =>
          logger.error("Terminating due to initialization failure.", e)
          context.system.terminate()
          Behaviors.empty
      }
    }, "TelegraftRestService")
  }

  def init(context: ActorContext[_], stateMachineService: TelegraftStateMachineService, raftService: TelegraftStateMachineService): Unit = {
    AkkaManagement(context.system).start()
    ClusterBootstrap(context.system).start()

    val telegraftRoutes = new TelegraftRoutes(stateMachineService, raftService)(context)
    startHttpServer(telegraftRoutes.route)(context.system)
    
  }

  protected def stateMachineServiceClient ( 
      system: ActorSystem[_],
      host: String,
      port: String): TelegraftStateMachineService = {
    val stateMachineClient =
      GrpcClientSettings
        .connectToServiceAt(
          system.settings.config.getString(host),
          system.settings.config.getInt(port))(system)
        .withTls(false)
    TelegraftStateMachineServiceClient(stateMachineClient)(system)
  }

  protected def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start

    import system.executionContext

    val futureBinding = Http().newServerAt("0.0.0.0", 8080).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

}
