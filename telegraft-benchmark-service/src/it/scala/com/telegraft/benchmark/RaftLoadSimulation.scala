package com.telegraft.benchmark

import com.github.phisgr.gatling.grpc.Predef._
import com.telegraft.rafktor.proto.Rafktor.{ ClientRequest, LogEntryPayload, LogEntryResponse }
import io.gatling.core.Predef._
import com.telegraft.rafktor.proto.RafktorClient.TelegraftRaftClientServiceGrpc
import com.telegraft.rafktor.proto.RafktorClient.ClientRequestPayload
import com.telegraft.statemachine.proto.TelegraftStateMachine.CreateUserRequest

class RaftLoadSimulation extends Simulation {

  val grpcConf1 = grpc(managedChannelBuilder("localhost", 8350).usePlaintext())
  val grpcConf2 = grpc(managedChannelBuilder("localhost", 8351).usePlaintext())
  val grpcConf3 = grpc(managedChannelBuilder("localhost", 8352).usePlaintext())

  val idFeeder = Iterator.continually(
    Map("clientId" -> java.util.UUID.randomUUID().toString, "requestId" -> java.util.UUID.randomUUID().toString))

  var userIds: scala.collection.mutable.Seq[Long] = scala.collection.mutable.Seq.empty[Long]

  val requestWithRepetition =
    repeat(10) {
      feed(idFeeder)
        .exec(
          grpc("create_users")
            .rpc(TelegraftRaftClientServiceGrpc.METHOD_CLIENT_REQUEST)
            .payload(
              ClientRequestPayload(
                "${clientId}",
                "${requestId}",
                Some(LogEntryPayload(LogEntryPayload.Payload.CreateUser(CreateUserRequest("NewUser"))))))
            .extract(_.payload.some)(x => x.saveAs("LOG_ENTRY_RESPONSE")))
        .exec(session => {
          userIds = userIds :+ session("LOG_ENTRY_RESPONSE")
            .as[Option[LogEntryResponse]]
            .map(_.payload.createUser.get.userId)
            .get
          println("User ids now are: " + userIds)

          session
        })
    }

  val scn = scenario("Scenario Name") // A scenario is a chain of requests and pauses
    .exec(requestWithRepetition)

  setUp(scn.inject(atOnceUsers(1)).protocols(grpcConf1, grpcConf2, grpcConf3))
}
