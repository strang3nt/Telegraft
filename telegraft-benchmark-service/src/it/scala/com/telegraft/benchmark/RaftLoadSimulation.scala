package com.telegraft.benchmark

import com.github.phisgr.gatling.grpc.Predef._
import com.telegraft.rafktor.proto.Rafktor.{ ClientRequest, LogEntryPayload }
import io.gatling.core.Predef._
import com.telegraft.rafktor.proto.RafktorClient.TelegraftRaftClientServiceGrpc
import com.telegraft.rafktor.proto.RafktorClient.ClientRequestPayload
import com.telegraft.statemachine.proto.TelegraftStateMachine.CreateUserRequest

class RaftLoadSimulation extends Simulation {

  val grpcConf = grpc(managedChannelBuilder("localhost", 8350).usePlaintext())

  val requestWithRepetition =
    repeat(10) {
      exec(
        grpc("create_users")
          .rpc(TelegraftRaftClientServiceGrpc.METHOD_CLIENT_REQUEST)
          .payload(
            ClientRequestPayload(
              "clientId",
              "requestId",
              Some(LogEntryPayload(LogEntryPayload.Payload.CreateUser(CreateUserRequest("NewUser"))))))
          .extract(_.payload.some)(x => x.saveAs("LOG_ENTRY_RESPONSE"))).exec(session => {
        println("This is the variable: " + session("LOG_ENTRY_RESPONSE").as[String])
        session
      })
    }

  val scn = scenario("Scenario Name") // A scenario is a chain of requests and pauses
    .exec(requestWithRepetition)

  setUp(scn.inject(atOnceUsers(1)).protocols(grpcConf))
}
