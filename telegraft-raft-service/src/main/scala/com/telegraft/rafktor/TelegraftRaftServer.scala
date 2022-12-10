package com.telegraft.rafktor

import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.{ServerReflection, ServiceHandler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TelegraftRaftServer {
  def start(
      interface: String,
      port: Int,
      system: ActorSystem[_],
      raftService: proto.TelegraftRaftService,
      raftClientService: proto.TelegraftRaftClientService): Unit = {

    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContext = system.executionContext

    val service: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(
        proto.TelegraftRaftServiceHandler.partial(raftService),
        proto.TelegraftRaftClientServiceHandler.partial(raftClientService),
        // ServerReflection enabled to support grpcurl without import-path and proto parameters
        ServerReflection.partial(List(proto.TelegraftRaftService, proto.TelegraftRaftClientService)))

    val bound =
      Http().newServerAt(interface, port).bind(service).map(_.addToCoordinatedShutdown(3.seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Telegraft raft server at gRPC server {}:{}", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }
  }
}
