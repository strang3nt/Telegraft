package com.telegraft.rafktor

import akka.actor.typed.ActorRef
import akka.persistence.typed.state.scaladsl.DurableStateBehavior
import akka.persistence.typed.PersistenceId
import akka.pattern.StatusReply
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey

object RaftNode {

  // protocol

  final case class Config(raftNodes: Seq[ActorRef[Command]])

  sealed trait State extends CborSerializable
  final case class Follower(
    currTerm: Int, 
    votedFor: Option[Int], 
    log: Seq[(SMCommand, Config, Int)],
    commitIndex: Int,
    lastApplied: Int,
    leaderId: Option[Int]) extends State
  final case class Leader(
    currTerm: Int,
    votedFor: Option[Int], 
    log: Seq[SMCommand],
    commitIndex: Int,
    lastApplied: Int,
    nextIndex: Map[ActorRef[Command], Int], 
    maxIndex: Map[ActorRef[Command], Int]) extends State
  final case class Candidate(
    currTerm: Int, 
    votedFor: Option[Int], 
    log: Seq[SMCommand],
    commitIndex: Int,
    lastApplied: Int) extends State

  sealed trait Command extends CborSerializable

  final case class AppendEntries(
    term: Int,
    replyTo: ActorRef[EntriesAppended], // leaderId
    prevLogIndex: Int, 
    prevLogTerm: Int, 
    entries: Seq[SMCommand], 
    leaderCommit: Int
    ) extends Command

  final case class RequestVote(
    term: Int, 
    replyTo: ActorRef[VoteGranted], // candidateId
    lastLogIndex: Int, 
    lastLogTerm: Int,
    ) extends Command

  final case class EntriesAppended(term: Int, success: Boolean) extends Command
  final case class VoteGranted(term: Int, voteGranted: Boolean) extends Command

  final case class MsgFromClient(s: SMCommand, replyTo: ActorRef[StatusReply[SMResponse]]) extends Command
  final case class ChangeConfig(newConfig: Config) extends Command

  // end protocol
  // stateful actor with durable state

  import akka.persistence.typed.state.scaladsl.Effect
  import akka.persistence.typed.state.scaladsl.DurableStateBehavior.CommandHandler

  val RaftServiceKey: ServiceKey[Command] = ServiceKey[Command]("RaftNode")

  def apply(stateMachine: ActorRef[SMCommand], persistenceId: PersistenceId): Behavior[Command] = 
    Behaviors.setup[Command]{ context => 
      context.system.receptionist ! Receptionist.Register(RaftServiceKey, context.self)
      Behaviors.empty
    }

  private val commandHandler: (State, Command) => Effect[State] = ???
}
