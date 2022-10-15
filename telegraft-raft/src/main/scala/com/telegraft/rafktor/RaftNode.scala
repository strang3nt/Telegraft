package com.telegraft.rafktor

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import com.telegraft.rafktor.SMProtocol

object RaftNode {

  // protocol

//  private val commandHandler: (State, Command) => Effect[State] = ???

  def apply(stateMachine: ActorRef[SMProtocol.Command], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.system.receptionist ! Receptionist.Register(RaftServiceKey, context.self)
      Behaviors.receiveMessagePartial[Command] {
        case MsgFromClient(command, replyTo) => stateMachine ! SMProtocol.MsgFromRaftSystem(command, replyTo); Behaviors.same
      }
    }

  def RaftServiceKey: ServiceKey[Command] = ServiceKey[Command]("RaftNode")

  sealed trait State extends CborSerializable

  sealed trait Command extends CborSerializable

  final case class Config(raftNodes: Seq[ActorRef[Command]])

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
                           log: Seq[(SMCommand, Config, Int)],
                           commitIndex: Int,
                           lastApplied: Int,
                           nextIndex: Map[ActorRef[Command], Int],
                           maxIndex: Map[ActorRef[Command], Int]) extends State

  final case class Candidate(
                              currTerm: Int,
                              votedFor: Option[Int],
                              log: Seq[(SMCommand, Config, Int)],
                              commitIndex: Int,
                              lastApplied: Int) extends State

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

  // end protocol

  final case class VoteGranted(term: Int, voteGranted: Boolean) extends Command

  final case class MsgFromClient(command: SMCommand, replyTo: ActorRef[SMResponse]) extends Command

  final case class ChangeConfig(newConfig: Config) extends Command
}
