package com.telegraft.rafktor

import akka.actor.testkit.typed.scaladsl.{ ManualTime, ScalaTestWithActorTestKit }
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.telegraft.rafktor.Log.{ CreateUser, TelegraftResponse, UserCreated }
import com.telegraft.rafktor.RaftServer.{ AppendEntries, AppendEntriesResponse, ClientRequest }
import com.telegraft.rafktor.RaftState.{ Candidate, Follower, Leader }
import com.telegraft.rafktor.proto.{ AppendEntriesRequest, RequestVoteRequest, TelegraftRaftServiceClient }
import com.telegraft.statemachine.proto.TelegraftStateMachineServiceClient
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object RaftServerSpec {
  val config: Config = ConfigFactory
    .parseString(s"""
       include "raft"

       akka.actor.serialization-bindings {
         "com.telegraft.rafktor.CborSerializable" = jackson-cbor
       }
       telegraft-raft-service {
         ask-timeout = 3 s
       }
       """)
    .withFallback(ManualTime.config)
    .withFallback(EventSourcedBehaviorTestKit.config)
    .resolve()
}
class RaftServerSpec
    extends ScalaTestWithActorTestKit(RaftServerSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with MockFactory {

  private val raftServerId = "testServerId"
  private val configuration = new ConfigurationMock(Set.empty)

  private val mockedStateMachineBehaviour = Behaviors.receiveMessage[StateMachine.Command] {
    case StateMachine.ClientRequest(payload: CreateUser, replyTo) =>
      replyTo ! StatusReply.success(UserCreated(true, payload.userName, None)); Behaviors.same
    case _ => Behaviors.same
  }
  private val stateMachine = testKit.spawn(mockedStateMachineBehaviour, "StateMachineMock")
  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[RaftServer.Command, RaftServer.Event, RaftState](
      system,
      RaftServer(raftServerId, stateMachine.ref, configuration))

  val mockPayload: Log.CreateUser = Log.CreateUser("TestUser")
  override protected def beforeEach(): Unit = {
    super.beforeEach()
    configuration.servers = Set.empty
    eventSourcedTestKit.clear()
  }

  val manualTime: ManualTime = ManualTime()

  "Election Safety property entails that any raft server" should {

    "begin as follower" in {

      val initialState = eventSourcedTestKit.getState()
      initialState shouldBe Follower(-1, None, Log.empty, -1, -1, None)
    }

    "remain follower as long as it receives valid RPCs from candidates or leader" in {

      val initialState = eventSourcedTestKit.getState()
      eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
        RaftServer
          .AppendEntries(initialState.currentTerm + 1, "LeaderId", -1, -1, Log(Vector((mockPayload, 1))), -1, _))
      eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
        RaftServer.AppendEntries(initialState.currentTerm + 2, "LeaderId", 0, 0, Log(Vector((mockPayload, 2))), 1, _))
      val result =
        eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
          RaftServer.AppendEntries(initialState.currentTerm + 1, "AnotherLeader", 0, 0, Log.empty, 0, _))
      result.reply shouldBe StatusReply.Success(
        AppendEntriesResponse(result.state.currentTerm, raftServerId, 1, success = false))
      result.event shouldBe RaftServer.EntriesAppended(initialState.currentTerm + 1, "AnotherLeader", 0, 0, Log.empty)

      manualTime.timePasses(150.millis)
      eventSourcedTestKit.getState() shouldBe Follower(
        initialState.currentTerm + 2,
        None,
        Log(Vector((mockPayload, 1), (mockPayload, 2))),
        1,
        -1,
        Some("LeaderId"))

      manualTime.timePasses(150.millis)
      eventSourcedTestKit.getState() shouldBe Candidate(
        initialState.currentTerm + 3,
        Some(raftServerId),
        Log(Vector((mockPayload, 1), (mockPayload, 2))),
        1,
        -1,
        1)

    }

    "turn into follower if it is a candidate and encounters the one true leader" in {
      manualTime.timePasses(300.millis)
      eventSourcedTestKit.getState() shouldBe a[Candidate]
      eventSourcedTestKit.runCommand(RaftServer.AppendEntries(2, "LeaderId", -1, -1, Log.empty, -1, _))
      eventSourcedTestKit.getState() shouldBe a[Follower]
    }

    "Follower updates leader if encounters a different more up-to-date" in {
      eventSourcedTestKit.runCommand(RaftServer.AppendEntries(2, "LeaderId", -1, -1, Log.empty, -1, _))
      eventSourcedTestKit.getState() shouldBe Follower(2, None, Log.empty, -1, -1, Some("LeaderId"))
      eventSourcedTestKit.runCommand(RaftServer.AppendEntries(3, "AnotherLeader", -1, -1, Log.empty, -1, _))
      eventSourcedTestKit.getState() shouldBe Follower(3, None, Log.empty, -1, -1, Some("AnotherLeader"))
    }

    "Server candidate becomes leader only if receives approving votes from majority" in {
      manualTime.timePasses(149.millis)

      val raftServerClientMock1 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock1.requestVote(_: RequestVoteRequest)).expects(*).once().onCall { req: RequestVoteRequest =>
        Future.successful(proto.RequestVoteResponse(req.term, granted = true))
      }
      val raftServerClientMock2 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock2.requestVote(_: RequestVoteRequest)).expects(*).once().onCall { req: RequestVoteRequest =>
        Future.successful(proto.RequestVoteResponse(req.term, granted = true))
      }
      val server1 = new ServerMock("server1", raftServerClientMock1, mock[TelegraftStateMachineServiceClient])
      val server2 = new ServerMock("server2", raftServerClientMock2, mock[TelegraftStateMachineServiceClient])
      configuration.servers = Set(server1, server2)

      manualTime.timePasses(400.millis)
      eventSourcedTestKit.getState() shouldBe a[Candidate]

    }
    "Server candidate retries election if no leader is elected in current term" in {
      manualTime.timePasses(300.millis)
      eventSourcedTestKit.getState() shouldBe a[Candidate]
      manualTime.timePasses(300.millis)
      eventSourcedTestKit.getState().currentTerm shouldBe oneOf(1, 0)
    }
  }
  "Leader Append-Only property entails that a raft server" should {
    "append a client request to its log if it is a leader, route it to leader otherwise" in {
      val raftServerClientMock1 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock1.appendEntries(_: AppendEntriesRequest)).expects(*).once().onCall {
        _: AppendEntriesRequest =>
          Future.successful(proto.AppendEntriesResponse(0, true))
      }
      val raftServerClientMock2 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock2.appendEntries(_: AppendEntriesRequest)).expects(*).once().onCall {
        _: AppendEntriesRequest =>
          Future.successful(proto.AppendEntriesResponse(0, true))
      }
      val grpcClient = mock[TelegraftStateMachineServiceClient]
      (grpcClient.createUser(_: com.telegraft.statemachine.proto.CreateUserRequest)).expects(*).onCall {
        r: com.telegraft.statemachine.proto.CreateUserRequest =>
          Future.successful(com.telegraft.statemachine.proto.CreateUserResponse(true, r.username, None))
      }
      val server1 = new ServerMock("server1", raftServerClientMock1, grpcClient)
      val server2 = new ServerMock("server2", raftServerClientMock2, mock[TelegraftStateMachineServiceClient])
      configuration.servers = Set(server1, server2)
      eventSourcedTestKit.initialize(
        Leader(0, None, Log.empty, -1, -1, Map("server1" -> 0, "server2" -> 0), Map("server1" -> -1, "server2" -> -1)))

      eventSourcedTestKit.runCommand(
        ClientRequest(
          CreateUser("NewUser"),
          testKit.spawn(Behaviors.stopped[StatusReply[TelegraftResponse]], "StateMachineResponse")))
      eventSourcedTestKit.getState().log.lastLogIndex shouldBe 0
      manualTime.timePasses(10.millis)
      eventSourcedTestKit.runCommand(
        AppendEntries(
          1,
          "server1",
          -1,
          0,
          Log(Vector((CreateUser("NewUser"), 0))),
          0,
          testKit.spawn(Behaviors.stopped[StatusReply[AppendEntriesResponse]], "AppendEntriesResponse")))

      eventSourcedTestKit
        .getState() shouldBe Follower(1, None, Log(Vector((CreateUser("NewUser"), 0))), 0, -1, Some("server1"))

    }
    "apply entry if entry is replicated if server is leader" in {
      val raftServerClientMock1 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock1.appendEntries(_: AppendEntriesRequest)).expects(*).once().onCall {
        _: AppendEntriesRequest =>
          Future.successful(proto.AppendEntriesResponse(0, true))
      }
      val raftServerClientMock2 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock2.appendEntries(_: AppendEntriesRequest)).expects(*).once().onCall {
        _: AppendEntriesRequest =>
          Future.successful(proto.AppendEntriesResponse(0, true))
      }

      val server1 = new ServerMock("server1", raftServerClientMock1, mock[TelegraftStateMachineServiceClient])
      val server2 = new ServerMock("server2", raftServerClientMock2, mock[TelegraftStateMachineServiceClient])
      configuration.servers = Set(server1, server2)
      eventSourcedTestKit.initialize(
        Leader(0, None, Log.empty, -1, -1, Map("server1" -> 0, "server2" -> 0), Map("server1" -> -1, "server2" -> -1)))

      eventSourcedTestKit.runCommand(
        ClientRequest(CreateUser("NewUser"), testKit.spawn(Behaviors.stopped[StatusReply[TelegraftResponse]])))
      manualTime.timePasses(10.millis)
      manualTime.timePasses(10.millis)
      manualTime.timePasses(100.millis)

      eventSourcedTestKit.getState() shouldBe Leader(
        0,
        None,
        Log(Vector((CreateUser("NewUser"), 0))),
        0,
        0,
        Map("server1" -> 1, "server2" -> 1),
        Map("server1" -> 0, "server2" -> 0))

    }
    "retry append entries indefinitely if follower doesn't answer" in {

      val raftServerClientMock1 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock1.appendEntries(_: AppendEntriesRequest)).expects(*).repeated(3 to 6).onCall {
        _: AppendEntriesRequest => Future.failed(new RuntimeException("message"))
      }
      val raftServerClientMock2 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock2.appendEntries(_: AppendEntriesRequest)).expects(*).repeated(3 to 6).onCall {
        _: AppendEntriesRequest => Future.failed(new RuntimeException("message"))
      }
      val server1 = new ServerMock("server1", raftServerClientMock1, mock[TelegraftStateMachineServiceClient])
      val server2 = new ServerMock("server2", raftServerClientMock2, mock[TelegraftStateMachineServiceClient])
      configuration.servers = Set(server1, server2)

      eventSourcedTestKit.initialize(
        Leader(0, None, Log.empty, -1, -1, Map("server1" -> 0, "server2" -> 0), Map("server1" -> -1, "server2" -> -1)))
      manualTime.timePasses(300.millis)
      manualTime.timePasses(300.millis)
      manualTime.timePasses(300.millis)
      eventSourcedTestKit.getState() shouldBe a[Leader]

    }
  }
  "Log Matching property entails that a raft server" should {
    "If the follower does not find an entry in its log with the same index and term, then it refuses the new entries" in {}
    "conflicting entries in follower are overwritten by leader's" in {}
    "leader looks for last non conflicting entry" in {}
    "if append entry has succeeded it means that follower's log is consistent to leader's log, up to last sent entry" in {}
  }
  "Leader Completeness" should {
    "If the candidate’s log is at least as up-to-date as any other log in that majority, then it will hold all the committed entries" in {}
  }
  "State Machine Safety guarantees that the state machine" should {
    "apply commands in log index order" in {}
  }

}
