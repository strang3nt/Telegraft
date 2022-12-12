package com.telegraft.benchmark

import com.github.phisgr.gatling.grpc.Predef._
import com.telegraft.rafktor.proto.Rafktor.{ClientRequest, LogEntryPayload, LogEntryResponse}
import io.gatling.core.Predef._
import com.telegraft.rafktor.proto.RafktorClient.TelegraftRaftClientServiceGrpc
import com.telegraft.rafktor.proto.RafktorClient.ClientRequestPayload
import com.telegraft.statemachine.proto.TelegraftStateMachine.{CreateChatRequest, CreateUserRequest, JoinChatRequest}

import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArraySet}

class RaftLoadSimulation extends Simulation {

  val grpcConf = Seq(
    grpc(managedChannelBuilder("localhost", 8350).usePlaintext()),
    grpc(managedChannelBuilder("localhost", 8351).usePlaintext()),
    grpc(managedChannelBuilder("localhost", 8352).usePlaintext()))

  val numberOfRaftServers = Integer.getInteger("raftServers", 3).toInt
  val usersCount = Integer.getInteger("userCount", 100).toInt
  val chatsPerUser = Integer.getInteger("chatsPerUser", 10).toInt

  var userIds = new CopyOnWriteArraySet[Long]()
  var userChats = new ConcurrentHashMap[Long, Set[Long]]()

  val idFeeder = Iterator.continually(
    Map("clientId" -> java.util.UUID.randomUUID().toString, "requestId" -> java.util.UUID.randomUUID().toString))

  val createUser =
    feed(idFeeder)
      .exec(
        grpc("create_user")
          .rpc(TelegraftRaftClientServiceGrpc.METHOD_CLIENT_REQUEST)
          .payload(session =>
            ClientRequestPayload(
              session("clientId").as[String],
              session("requestId").as[String],
              Some(LogEntryPayload(LogEntryPayload.Payload.CreateUser(CreateUserRequest("NewUser"))))))
          .extract(_.payload.some)(x => x.saveAs("LOG_ENTRY_RESPONSE")))
      .exec(session => {
        userIds.add(
          session("LOG_ENTRY_RESPONSE").as[Option[LogEntryResponse]].map(_.payload.createUser.get.userId).get)
        session
      })


  val createChats =
    repeat(chatsPerUser) {
      feed(idFeeder)
        .exec(
          grpc("create_chat")
            .rpc(TelegraftRaftClientServiceGrpc.METHOD_CLIENT_REQUEST)
            .payload(session =>
              ClientRequestPayload(
                session("clientId").as[String],
                session("requestId").as[String],
                Some(
                  LogEntryPayload(LogEntryPayload.Payload.CreateChat(CreateChatRequest(
                    userIds.toArray()(session("counter").as[Int]).asInstanceOf[Long],
                    "chat_name",
                    "description"))))))
            .extract(_.payload.some)(x => x.saveAs("LOG_ENTRY_CHAT_RESPONSE")))
        .exec(session => {
          userChats.compute(
            userIds.toArray()(session("counter").as[Int]).asInstanceOf[Long],
            (_, v) => {
              val newElement = session("LOG_ENTRY_CHAT_RESPONSE")
                .as[Option[LogEntryResponse]]
                .map(_.payload.createChat.get.chatId)
                .get
              v match {
                case _ if v == null => Set(newElement)
                case _              => v + newElement
              }
            })
          session
        })
    }

  /**
   * random user joins a number of random chats, but not chats he is already a member
   */
  // val userJoinsChat = ???
  /**
   * random user first gets their message and then sends a message to a random chat of theirs
   */
  // val userReadsAndSendsMessages = ???

  val scn = scenario("Scenario Name") // A scenario is a chain of requests and pauses
    .exec(createUser)
    .exec(createChats)
    .exec(session => {
      println(userIds.toArray.map(_.asInstanceOf[Long]).mkString(","))
      println(userChats.entrySet().toArray.map(_.asInstanceOf[(Long, Set[Long])]).mkString(","))
      session
    })

  setUp(scn.inject(atOnceUsers(usersCount)).protocols(grpcConf.take(numberOfRaftServers)))
}
