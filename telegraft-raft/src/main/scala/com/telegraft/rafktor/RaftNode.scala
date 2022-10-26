package com.telegraft.rafktor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.scaladsl.Effect

object RaftNode {

  // protocol
  sealed trait State extends CborSerializable {
    // Persistent state
    val currentTerm: Long
    val votedFor: Option[Long]
    val log: Seq[(Any, Long)]

    // Volatile state
    val commitIndex: Long
    val lastApplied: Long
  }

  final case class Follower(
      currentTerm: Long,
      votedFor: Option[Long],
      log: Seq[(Any, Long)],
      commitIndex: Long,
      lastApplied: Long)
      extends State

  final case class Leader(
      currentTerm: Long,
      votedFor: Option[Long],
      log: Seq[(Any, Long)],
      commitIndex: Long,
      lastApplied: Long,
      nextIndex: Map[Server, Long],
      maxIndex: Map[Server, Long])
      extends State

  final case class Candidate(
      currentTerm: Long,
      votedFor: Option[Long],
      log: Seq[(Any, Long)],
      commitIndex: Long,
      lastApplied: Long)
      extends State

  sealed trait Command extends CborSerializable {
    val term: Long
  }
  final case class AppendEntries(
      term: Long,
      leaderId: Server,
      prevLogIndex: Long,
      prevLogTerm: Long,
      entries: Seq[Any],
      leaderCommit: Long)
      extends Command

  final case class RequestVote(
      term: Long,
      candidateId: Server,
      lastLogIndex: Long,
      lastLogTerm: Long)
      extends Command

  /**
   * @param term
   *
   * Message periodically sent to a Raft node, in order
   * to advance to the next term.
   */
  private final case class NextTerm(term: Long) extends Command

  sealed trait Event
  private final case object NextTermEvent extends Event

  final case class EntriesAppended(
      term: Long,
      leaderId: Server,
      entries: Seq[Any])
      extends Event

  final case class VoteRequested(
      term: Long,
      candidateId: Server,
      lastLogTerm: Long,
      lastLogIndex: Long)
      extends Event

  sealed trait Response extends CborSerializable {
    val term: Long
  }

  final case class AppendEntriesResponse(term: Long, success: Boolean)
      extends Response

  final case class RequestVoteResponse(term: Long, voteGranted: Boolean)
      extends Response

  // end protocol

  private val commandHandler: (State, Command) => Effect[Event, State] = ???

  def apply(): Behavior[Command] =
    Behaviors.setup[Command] { context => ??? }
}
