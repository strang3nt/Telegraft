package com.telegraft.rafktor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.projection.testkit.javadsl.ProjectionTestKit
import org.scalatest.BeforeAndAfterEach

class RaftServerSpec
    extends ScalaTestWithActorTestKit
    with org.scalatest.wordspec.AnyWordSpecLike
    with BeforeAndAfterEach {

  private val raftServerId = "testRaftServer"
  private val stateMachine =
    testKit.spawn(Behaviors.ignore[StateMachine.Command])
  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[
      RaftServer.Command,
      RaftServer.Event,
      RaftState](
      system,
      RaftServer("serverId", stateMachine, new Configuration))

  "RaftServer should respect the following properties" should {
    "Election Safety" in {}
    "Leader Append-Only" in {}
    "Log Matching" in {}
    "Leader Completeness" in {}
    "State Machine Safety" in {}
  }

}
