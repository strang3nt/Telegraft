package com.telegraft.rafktor

import akka.actor.typed.{ ActorRef, Behavior }
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }

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

  sealed trait Command extends CborSerializable
  final case class AppendEntries(
      term: Long,
      leaderId: Server,
      prevLogIndex: Long,
      prevLogTerm: Long,
      entries: Seq[Any],
      leaderCommit: Long,
      replyTo: ActorRef[AppendEntriesResponse])
      extends Command

  final case class RequestVote(
      term: Long,
      candidateId: Server,
      lastLogIndex: Long,
      lastLogTerm: Long,
      replyTo: ActorRef[RequestVoteResponse])
      extends Command

  // client requests for leader
  final case class ClientRequest(
      request: TelegraftRequest,
      replyTo: ActorRef[StatusReply[TelegraftResponse]])
      extends Command

  sealed trait Event

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

  sealed trait Response extends CborSerializable

  final case class AppendEntriesResponse(term: Long, success: Boolean)
      extends Response

  final case class RequestVoteResponse(term: Long, voteGranted: Boolean)
      extends Response

  // end protocol

  private val commandHandler: (State, Command) => Effect[Event, State] =
    (s, c) =>
      s match {
        case Follower(currentTerm, votedFor, log, commitIndex, lastApplied) =>
          c match {
            case AppendEntries(
                  term,
                  leaderId,
                  prevLogIndex,
                  prevLogTerm,
                  entries,
                  leaderCommit,
                  replyTo) =>
              ???
            case RequestVote(
                  term,
                  candidateId,
                  lastLogIndex,
                  lastLogTerm,
                  replyTo) =>
              ???

            // routes to leader, waits for response and sends it to client
            case ClientRequest(request, replyTo) => ???
          }
        case Leader(
              currentTerm,
              votedFor,
              log,
              commitIndex,
              lastApplied,
              nextIndex,
              maxIndex) =>
          c match {
            case AppendEntries(
                  term,
                  leaderId,
                  prevLogIndex,
                  prevLogTerm,
                  entries,
                  leaderCommit,
                  replyTo) =>
              ???
            case RequestVote(
                  term,
                  candidateId,
                  lastLogIndex,
                  lastLogTerm,
                  replyTo) =>
              ???

            // send request to state machine, wait for response and answer back to client
            case ClientRequest(request, replyTo) => ???
          }
        case Candidate(currentTerm, votedFor, log, commitIndex, lastApplied) =>
          c match {
            case AppendEntries(
                  term,
                  leaderId,
                  prevLogIndex,
                  prevLogTerm,
                  entries,
                  leaderCommit,
                  replyTo) =>
              ???
            case RequestVote(
                  term,
                  candidateId,
                  lastLogIndex,
                  lastLogTerm,
                  replyTo) =>
              ???

            // there is no leader yet, thus push back into stack and compute next message
            case ClientRequest(request, replyTo) => ???
          }
      }

  private val eventHandler: (State, Event) => State = (s, e) =>
    e match {
      case EntriesAppended(term, leaderId, entries)                    => ???
      case VoteRequested(term, candidateId, lastLogTerm, lastLogIndex) => ???
    }

  def apply(): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = ???,
      emptyState = Follower(0, None, Seq.empty, 0, 0),
      commandHandler = commandHandler,
      eventHandler = eventHandler)
}
