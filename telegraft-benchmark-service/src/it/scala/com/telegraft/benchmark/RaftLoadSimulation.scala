package com.telegraft.benchmark

import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.grpc.action.GrpcCallActionBuilder
import com.github.phisgr.gatling.grpc.protocol.DynamicGrpcProtocol
import com.telegraft.rafktor.proto.Rafktor.LogEntryPayload
import io.gatling.core.Predef._
import com.telegraft.rafktor.proto.RafktorClient.{
  ClientQueryPayload,
  ClientRequestPayload,
  ClientRequestResponse,
  TelegraftRaftClientServiceGrpc
}
import com.telegraft.statemachine.proto.TelegraftStateMachine.{ GetMessagesRequest, SendMessageRequest }
import io.gatling.core.structure.{ ChainBuilder, ScenarioBuilder }

import scala.util.Random
import scala.concurrent.duration._

class RaftLoadSimulation extends Simulation {

  val ports: Array[Int] = Array(8350, 8351, 8352)
  val dynamic: DynamicGrpcProtocol = dynamicChannel("target").forceParsing

  val numberOfRaftServers: Int = Integer.getInteger("raftServers", 3).toInt
  val usersCount: Int = Integer.getInteger("userCount", 100).toInt

  val idFeeder: Iterator[Map[String, String]] = Iterator.continually(
    Map("clientId" -> java.util.UUID.randomUUID().toString, "requestId" -> java.util.UUID.randomUUID().toString))

  val random = new Random()

  private val userSendsMessage = grpc("user_sends_messages")
    .rpc(TelegraftRaftClientServiceGrpc.METHOD_CLIENT_REQUEST)
    .payload(session => {
      val userChatsArray =
        session("chats").as[String].substring(1, session("chats").as[String].length - 1).split(";").map(_.toLong)
      ClientRequestPayload(
        session("clientId").as[String],
        session("requestId").as[String],
        Some(LogEntryPayload(LogEntryPayload.Payload.SendMessage(SendMessageRequest(
          session("customers").as[Long],
          userChatsArray(random.nextInt(userChatsArray.length)),
          "message",
          Some(com.google.protobuf.timestamp.Timestamp(java.time.Instant.now())))))))
    })
    .extract(_.status.some)(_.is(true))
    .target(dynamic)

  private val userReadsMessages =
    grpc("user_reads_messages")
      .rpc(TelegraftRaftClientServiceGrpc.METHOD_CLIENT_QUERY)
      .payload(session =>
        ClientQueryPayload(
          session("clientId").as[String],
          session("requestId").as[String],
          ClientQueryPayload.Payload.GetMessages(
            GetMessagesRequest(
              session("customers").as[Long],
              Some(com.google.protobuf.timestamp.Timestamp(java.time.Instant.now()))))))
      .extract(_.status.some)(_.is(true))
      .target(dynamic)

  /**
   * random user first gets their messages and then sends a message to a random chat of theirs
   */
  val userReadsAndSendsMessages: ChainBuilder =
    repeat(100) {
      feed(csv("users_chats.csv").random)
        .feed(idFeeder)
        .feed(ports.map(port => Map("port" -> port)).shuffle.circular)
        .exec(dynamic.setChannel { session =>
          val port = session("port").as[Int]
          managedChannelBuilder(s"localhost:$port").usePlaintext()
        })
        .exitHereIfFailed
        .exec(userReadsMessages)
        .feed(idFeeder)
        .exec(userSendsMessage)
        .exec(dynamic.disposeChannel)
    }

  val scn: ScenarioBuilder = scenario("User reads and sends messages") // A scenario is a chain of requests and pauses
    .exec(userReadsAndSendsMessages)

  setUp(scn.inject(stressPeakUsers(usersCount).during(20.seconds)))
}
