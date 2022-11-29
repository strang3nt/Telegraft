package com.telegraft.rafktor

import akka.actor.testkit.typed.scaladsl.{ ManualTime, ScalaTestWithActorTestKit }
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.telegraft.rafktor.RaftServer.AppendEntriesResponse
import com.telegraft.rafktor.RaftState.{ Candidate, Follower }
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.BeforeAndAfterEach

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
    .withFallback(EventSourcedBehaviorTestKit.config)
    .resolve()
}
class RaftServerSpec
    extends ScalaTestWithActorTestKit(ManualTime.config.withFallback(RaftServerSpec.config).resolve())
    with org.scalatest.wordspec.AnyWordSpecLike
    with BeforeAndAfterEach {

  private val raftServerId = "testServerId"
  private val stateMachine =
    testKit.spawn(Behaviors.ignore[StateMachine.Command])
  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[RaftServer.Command, RaftServer.Event, RaftState](
      system,
      RaftServer(raftServerId, stateMachine, Configuration.apply(system)))

  val mockPayload: Log.CreateUser = Log.CreateUser("TestUser")
  override protected def beforeEach(): Unit = {
    super.beforeEach()
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

    "turn into follower if it was a candidate and encounters the one true leader" in {
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

    "Server candidate becomes leader only if receives approving votes from majority" in {}
    "Server candidate retries election if no leader is elected in current term" in {}
  }
  "Leader Append-Only property entails that a raft server" should {
    "append a client request to its log if it is a leader, route it to leader otherwise" in {}
    "send append entries in parallel to all other nodes if it is Leader" in {}
    "apply entry if entry is replicated if server is leader" in {}
    "retry append entries indefinitely if follower doesn't answer" in {}
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
