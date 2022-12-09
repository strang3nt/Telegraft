package com.telegraft.benchmark

import com.github.phisgr.gatling.grpc.Predef._
import com.telegraft.rafktor.proto.Rafktor.{ClientRequest, LogEntryPayload}
import io.gatling.core.Predef._
import com.telegraft.rafktor.proto.RafktorClient.TelegraftRaftClientServiceGrpc
import com.telegraft.rafktor.proto.RafktorClient.ClientRequestPayload
import com.telegraft.statemachine.proto.TelegraftStateMachine.CreateUserRequest

import scala.concurrent.duration._

class RaftLoadSimulation extends Simulation {

  val grpcConf = grpc(managedChannelBuilder("0.0.0.0", 8350))
  val request = grpc("request_1")
    .rpc(TelegraftRaftClientServiceGrpc.METHOD_CLIENT_REQUEST)
    .payload(ClientRequestPayload("clientId", "requestId", Some(LogEntryPayload(LogEntryPayload.Payload.CreateUser(CreateUserRequest("NewUser"))))))
    //.extract(_.)(_ is SERVING)

  val scn = scenario("Scenario Name") // A scenario is a chain of requests and pauses
    .exec(request)
    .pause(7.seconds)
    .exec(request)
    .exec(request)
    .exec(request)

  setUp(scn.inject(atOnceUsers(1)).protocols(grpcConf))
}