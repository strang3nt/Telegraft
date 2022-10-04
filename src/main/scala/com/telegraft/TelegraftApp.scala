package com.telegraft

import akka.actor.typed.ActorSystem
import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.telegraft.rafktor.RaftService
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import com.telegraft.database.{Connection, UserRepository}
import com.telegraft.persistence.PersistentRepository
import com.telegraft.projection.RepositoryProjection

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContextExecutor

object TelegraftApp {

  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
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


  def main(args: Array[String]): Unit = {
    ActorSystem[Nothing](Behaviors.setup[Nothing] { context =>
      import akka.actor.typed.scaladsl.adapter._

      implicit val system: ActorSystem[Nothing] = context.system
      implicit val classicSystem: ClassicActorSystem = context.system.toClassic
      implicit val ec: ExecutionContextExecutor = context.system.executionContext

      //val cluster = Cluster(context.system)
      //context.log.info("Started [" + context.system + "], cluster.selfAddress = " + cluster.selfMember.address + ")")

      val persistentRepository = context.spawn(PersistentRepository(), "PersistentRepository")
      RepositoryProjection.init(context, new UserRepository(Connection.dbConfig))
      val stateMachine = context.spawn(StateMachine(persistentRepository), "StateMachine")
      val raftService =  context.spawn(RaftService(stateMachine), "RaftService")

      val requestHandler = context.spawn(SessionActor(raftService), "RequestHandler")
      val telegraftRoutes = new Routes(requestHandler)(context)
      startHttpServer(telegraftRoutes.route)

      AkkaManagement.get(classicSystem).start()
      //ClusterBootstrap.get(classicSystem).start()
      Behaviors.empty
    }, "Telegraft")
}}


