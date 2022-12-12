package com.telegraft.rafktor

import akka.actor.testkit.typed.scaladsl.{ ManualTime, ScalaTestWithActorTestKit }
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.telegraft.rafktor.Log._
import com.telegraft.rafktor.RaftServer.{ AppendEntries, AppendEntriesResponse, ClientRequest }
import com.telegraft.rafktor.RaftState.{ Candidate, Follower, Leader }
import com.telegraft.rafktor.proto.{ LogEntry, _ }
import com.telegraft.statemachine.proto.{ CreateUserRequest, CreateUserResponse }
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object RaftServerSpec {
  val config: Config = ConfigFactory
    .parseString(s"""
       akka.loglevel = "OFF"
       stdout-loglevel = "OFF"
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

  private val stateMachineProbe = testKit.createTestProbe[StateMachine.Command]()
  private val mockedStateMachineBehaviour = Behaviors.receiveMessage[StateMachine.Command] {
    case StateMachine.ClientRequest(payload: CreateUser, replyTo) =>
      replyTo ! StatusReply.success(UserCreated(ok = true, 1, None)); Behaviors.same
    case _ => Behaviors.same
  }
  private val stateMachine =
    testKit.spawn(Behaviors.monitor(stateMachineProbe.ref, mockedStateMachineBehaviour), "StateMachineMock")
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
      val logEntry1 = Log.LogEntry(mockPayload, 0.toLong, Some(("clientId1", "requestId1")), None)
      val logEntry2 = Log.LogEntry(mockPayload, 1.toLong, Some(("clientId2", "requestId2")), None)
      eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
        RaftServer.AppendEntries(initialState.currentTerm + 1, "LeaderId", -1, -1, Log(Vector(logEntry1)), -1, _))
      eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
        RaftServer.AppendEntries(initialState.currentTerm + 2, "LeaderId", 0, 0, Log(Vector(logEntry2)), -1, _))

      val result =
        eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
          RaftServer.AppendEntries(initialState.currentTerm + 1, "AnotherLeader", 0, 0, Log.empty, 0, _))
      result.reply shouldBe StatusReply.Success(
        AppendEntriesResponse(result.state.currentTerm, raftServerId, 1, success = false))
      result.event shouldBe RaftServer.EntriesAppended(
        initialState.currentTerm + 1,
        result.command.asInstanceOf[AppendEntries])

      manualTime.timePasses(150.millis)
      eventSourcedTestKit.getState() shouldBe Follower(
        initialState.currentTerm + 2,
        None,
        Log(Vector(logEntry1, logEntry2)),
        -1,
        -1,
        Some("LeaderId"))

      manualTime.timePasses(150.millis)
      eventSourcedTestKit.getState() shouldBe Candidate(
        initialState.currentTerm + 3,
        Some(raftServerId),
        Log(Vector(logEntry1, logEntry2)),
        -1,
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
      val server1 = new ServerMock("server1", raftServerClientMock1, mock[TelegraftRaftClientServiceClient])(system)
      val server2 = new ServerMock("server2", raftServerClientMock2, mock[TelegraftRaftClientServiceClient])(system)
      configuration.servers = Set(server1, server2)

      manualTime.timePasses(400.millis)
      eventSourcedTestKit.getState() shouldBe a[Candidate]

    }
    "Server candidate retries election if no leader is elected in current term" in {
      manualTime.timePasses(300.millis)
      eventSourcedTestKit.getState() shouldBe a[Candidate]
      manualTime.timePasses(300.millis)
      eventSourcedTestKit.getState().currentTerm should equal(1).or(equal(2))
    }
  }
  "Leader Append-Only property entails that a raft server" should {
    "append a client request to its log if it is a leader, route it to leader otherwise" in {
      val raftServerClientMock1 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock1.appendEntries(_: AppendEntriesRequest)).expects(*).once().onCall {
        _: AppendEntriesRequest =>
          Future.successful(proto.AppendEntriesResponse(success = true))
      }
      val raftServerClientMock2 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock2.appendEntries(_: AppendEntriesRequest)).expects(*).once().onCall {
        _: AppendEntriesRequest =>
          Future.successful(proto.AppendEntriesResponse(success = true))
      }
      val grpcClient = mock[TelegraftRaftClientServiceClient]
      (grpcClient
        .clientRequest(_: ClientRequestPayload))
        .expects(
          ClientRequestPayload(
            "clientId1",
            "requestId1",
            Some(LogEntryPayload(LogEntryPayload.Payload.CreateUser(CreateUserRequest("NewUser"))))))
        .onCall { _: ClientRequestPayload =>
          Future.successful(
            ClientRequestResponse(
              status = true,
              Some(LogEntryResponse(LogEntryResponse.Payload.CreateUser(CreateUserResponse(ok = true, 1, None))))))
        }
      val server1 = new ServerMock("server1", raftServerClientMock1, grpcClient)(system)
      val server2 = new ServerMock("server2", raftServerClientMock2, mock[TelegraftRaftClientServiceClient])(system)
      configuration.servers = Set(server1, server2)
      eventSourcedTestKit.initialize(
        Leader(0, None, Log.empty, -1, -1, Map("server1" -> 0, "server2" -> 0), Map("server1" -> -1, "server2" -> -1)))

      val logEntry = Log.LogEntry(CreateUser("NewUser"), 0.toLong, Some(("clientId1", "requestId1")), None)
      eventSourcedTestKit.runCommand(
        ClientRequest(
          logEntry.payload.asInstanceOf[TelegraftRequest],
          logEntry.maybeClientId,
          testKit.spawn(Behaviors.stopped[StatusReply[TelegraftResponse]], "StateMachineResponse")))
      val currState = eventSourcedTestKit.getState()
      currState.log.lastLogIndex shouldBe 0
      manualTime.timePasses(10.millis)
      currState.lastApplied shouldBe 0
      eventSourcedTestKit.runCommand(
        AppendEntries(
          1,
          "server1",
          -1,
          0,
          Log(Vector(logEntry)),
          0,
          testKit.spawn(Behaviors.stopped[StatusReply[AppendEntriesResponse]], "AppendEntriesResponse")))

      eventSourcedTestKit.runCommand(
        ClientRequest(
          logEntry.payload.asInstanceOf[TelegraftRequest],
          logEntry.maybeClientId,
          testKit.spawn(Behaviors.stopped[StatusReply[TelegraftResponse]], "StateMachineResponse")))

      eventSourcedTestKit.getState() shouldBe Follower(
        1,
        None,
        Log(Vector(logEntry.copy(maybeResponse = Some(UserCreated(ok = true, 1, None))))),
        0,
        0,
        Some("server1"))

    }
    "apply entry if entry is replicated if server is leader" in {
      val raftServerClientMock1 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock1.appendEntries(_: AppendEntriesRequest)).expects(*).twice().onCall {
        _: AppendEntriesRequest =>
          Future.successful(proto.AppendEntriesResponse(success = true))
      }
      val raftServerClientMock2 = mock[TelegraftRaftServiceClient]
      (raftServerClientMock2.appendEntries(_: AppendEntriesRequest)).expects(*).twice().onCall {
        _: AppendEntriesRequest =>
          Future.successful(proto.AppendEntriesResponse(success = true))
      }

      val server1 = new ServerMock("server1", raftServerClientMock1, mock[TelegraftRaftClientServiceClient])(system)
      val server2 = new ServerMock("server2", raftServerClientMock2, mock[TelegraftRaftClientServiceClient])(system)
      configuration.servers = Set(server1, server2)
      eventSourcedTestKit.initialize(
        Leader(0, None, Log.empty, -1, -1, Map("server1" -> 0, "server2" -> 0), Map("server1" -> -1, "server2" -> -1)))

      val logEntry1 = Log.LogEntry(CreateUser("NewUser"), 0.toLong, Some(("clientId1", "requestId1")), None)
      eventSourcedTestKit.runCommand(
        ClientRequest(
          logEntry1.payload.asInstanceOf[TelegraftRequest],
          logEntry1.maybeClientId,
          testKit.spawn(Behaviors.stopped[StatusReply[TelegraftResponse]])))
      manualTime.timePasses(10.millis)
      manualTime.timePasses(10.millis)
      manualTime.timePasses(100.millis)

      eventSourcedTestKit.getState() shouldBe Leader(
        0,
        None,
        Log(Vector(logEntry1.copy(maybeResponse = Some(UserCreated(ok = true, 1, None))))),
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
      val server1 = new ServerMock("server1", raftServerClientMock1, mock[TelegraftRaftClientServiceClient])(system)
      val server2 = new ServerMock("server2", raftServerClientMock2, mock[TelegraftRaftClientServiceClient])(system)
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
    "if the follower does not find an entry in its log with the same prevLogIndex and prevLogTerm, then it refuses the new entries" in {
      val initialState = Follower(
        0,
        Some("leaderId"),
        Log(
          Vector(
            Log.LogEntry(CreateUser("NewUser1"), 0, Some(("clientId", "requestId1")), None),
            Log.LogEntry(CreateUser("NewUser2"), 0, Some(("clientId", "requestId1")), None))),
        0,
        0,
        Some("leaderId"))
      eventSourcedTestKit.initialize(initialState)
      val result = eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
        RaftServer.AppendEntries(
          0,
          "leaderId",
          1,
          1,
          Log(Vector(Log.LogEntry(CreateUser("AnotherNewUser"), 1, Some(("clientId", "requestId2")), None))),
          0,
          _))

      result.reply shouldBe StatusReply.success(RaftServer.AppendEntriesResponse(0, "testServerId", 1, success = false))
      result.state shouldBe initialState

    }
    "conflicting entries in follower are overwritten by leader's" in {
      val initialState = Follower(
        0,
        Some("leaderId"),
        Log(
          Vector(
            Log.LogEntry(CreateUser("NewUser"), 0, Some(("clientId", "requestId1")), None),
            Log.LogEntry(CreateUser("AnotherUser"), 0, Some(("clientId", "requestId2")), None))),
        0,
        0,
        Some("leaderId"))
      eventSourcedTestKit.initialize(initialState)
      val result = eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
        RaftServer.AppendEntries(
          1,
          "leaderId",
          0,
          0,
          Log(Vector(Log.LogEntry(CreateUser("AnotherNewUser"), 1, Some(("clientId", "requestId2")), None))),
          0,
          _))

      result.reply shouldBe StatusReply.success(RaftServer.AppendEntriesResponse(1, "testServerId", 1, success = true))
      result.state shouldBe initialState.copy(
        currentTerm = 1,
        log = Log(
          Vector(
            Log.LogEntry(CreateUser("NewUser"), 0, Some(("clientId", "requestId1")), None),
            Log.LogEntry(CreateUser("AnotherNewUser"), 1, Some(("clientId", "requestId2")), None))))
    }
    "leader looks for last non conflicting entry" in {

      val initialState = Leader(
        1,
        None,
        Log(
          Vector(
            Log.LogEntry(CreateUser("NewUser"), 0, Some(("clientId", "requestId1")), None),
            Log.LogEntry(CreateUser("AnotherNewUser"), 1, Some(("clientId", "requestId2")), None))),
        -1,
        -1,
        Map("server1" -> 1, "server2" -> 0),
        Map("server1" -> 1, "server2" -> -1))

      eventSourcedTestKit.initialize(initialState)
      val result =
        eventSourcedTestKit.runCommand(RaftServer.AppendEntriesResponse(0, "server1", 1, success = false))

      result.state shouldBe initialState.copy(nextIndex = Map("server1" -> 0, "server2" -> 0))
    }
  }
  "State Machine Safety guarantees that the state machine" should {
    "apply commands in log index order" in {
      val initialState = Follower(
        0,
        Some("leaderId"),
        Log(
          Vector(
            Log.LogEntry(CreateUser("NewUser123"), 0, Some(("clientId", "requestId1")), None),
            Log.LogEntry(CreateUser("AnotherUser123"), 0, Some(("clientId", "requestId2")), None))),
        1,
        -1,
        Some("leaderId"))
      eventSourcedTestKit.initialize(initialState)
      eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
        RaftServer.AppendEntries(1, "leaderId", 1, 0, Log.empty, 1, _))
      manualTime.timePasses(10.millis)
      eventSourcedTestKit.getState().log.logEntries(0).maybeResponse.isDefined shouldBe true
      manualTime.timePasses(10.millis)
      eventSourcedTestKit.getState().log.logEntries(1).maybeResponse.isDefined shouldBe true

    }
  }

}
