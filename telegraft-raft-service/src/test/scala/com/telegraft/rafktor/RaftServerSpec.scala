package com.telegraft.rafktor

import akka.actor.testkit.typed.scaladsl.{
  ManualTime,
  ScalaTestWithActorTestKit
}
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
       akka.actor.serialization-bindings {
         "com.telegraft.rafktor.CborSerializable" = jackson-cbor
       }
       telegraft-raft-service {
         ask-timeout = 3 s
       }
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${java.util.UUID
      .randomUUID()
      .toString}"
      """)
    .withFallback(EventSourcedBehaviorTestKit.config)
    .resolve()
}
class RaftServerSpec
    extends ScalaTestWithActorTestKit(
      ManualTime.config.withFallback(RaftServerSpec.config).resolve())
    with org.scalatest.wordspec.AnyWordSpecLike
    with BeforeAndAfterEach {

  private val raftServerId = "testServerId"
  private val stateMachine =
    testKit.spawn(Behaviors.ignore[StateMachine.Command])
  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[
      RaftServer.Command,
      RaftServer.Event,
      RaftState](
      system,
      RaftServer(raftServerId, stateMachine, new Configuration))

  val mockPayload: Log.CreateUser = Log.CreateUser("TestUser")
  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  val manualTime: ManualTime = ManualTime()

  "Election Safety" should {
    "Server begins as follower" in {
      val initialState = eventSourcedTestKit.getState()
      initialState shouldBe Follower(-1, None, Log.empty, -1, -1, None)
    }

    // TODO: eventsourcedBehavior is not asinchronous
    "Server remains follower as long as it receives valid RPCs from candidates or leader" in {
      val initialState = eventSourcedTestKit.getState()

      eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
        RaftServer.AppendEntries(
          initialState.currentTerm + 1,
          "LeaderId",
          -1,
          -1,
          Log(Vector((mockPayload, 1))),
          -1,
          _))
      eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
        RaftServer.AppendEntries(
          initialState.currentTerm + 2,
          "LeaderId",
          0,
          0,
          Log(Vector((mockPayload, 2))),
          1,
          _))
      val result =
        eventSourcedTestKit.runCommand[StatusReply[AppendEntriesResponse]](
          RaftServer.AppendEntries(
            initialState.currentTerm + 1,
            "AnotherLeader",
            0,
            0,
            Log.empty,
            0,
            _))
      result.reply shouldBe StatusReply.Success(
        AppendEntriesResponse(
          result.state.currentTerm,
          raftServerId,
          1,
          success = false))
      result.event shouldBe RaftServer.EntriesAppended(
        initialState.currentTerm + 1,
        "AnotherLeader",
        0,
        0,
        Log.empty)
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
    "Server candidate becomes leader only if receives votes from majority" in {}

    "Server candidate turn into follower if encounters the one true leader" in {}
    "Follower updates leader if encounters a different more up-to-date" in {}
    "Server candidate retries election if no leader is elected in current term" in {}
  }
  "Leader Append-Only" should {}
  "Log Matching" should {}
  "Leader Completeness" should {}
  "State Machine Safety" should {}

}
