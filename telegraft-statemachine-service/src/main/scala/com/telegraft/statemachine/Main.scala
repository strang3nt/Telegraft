package com.telegraft.statemachine

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import com.telegraft.statemachine.database.DatabaseRepositoryImpl
import com.telegraft.statemachine.proto.TelegraftStateMachineServiceHandler

import scala.concurrent.{ ExecutionContext, Future }

object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("TelegraftStateMachineService")
    new Main(system).run()
    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }
}

class Main(system: ActorSystem) {
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      TelegraftStateMachineServiceHandler(new TelegraftStateMachineImpl(DatabaseRepositoryImpl.init))

    // Bind service handler servers to localhost:8080/8081
    val binding = Http()
      .newServerAt(
        system.settings.config.getString("telegraft-statemachine-service.grpc.interface"),
        system.settings.config.getInt("telegraft-statemachine-service.grpc.port"))
      .bind(service)

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}
