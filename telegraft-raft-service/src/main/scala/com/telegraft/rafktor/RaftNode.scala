package com.telegraft.rafktor

import akka.actor.typed.{ ActorRef, Behavior }
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import com.telegraft.rafktor.Log.{ TelegraftRequest, TelegraftResponse }

object RaftNode {

  // protocol
  sealed trait State extends CborSerializable {
    // Persistent state
    val currentTerm: Long
    val votedFor: Option[String]
    val log: Log

    // Volatile state
    val commitIndex: Long
    val lastApplied: Long

    def mayConvertToFollower(
        newTerm: Long,
        newLeader: Option[String] = None): State =
      if (this.currentTerm < newTerm) {
        Follower(
          currentTerm = newTerm,
          votedFor = None,
          log = this.log,
          commitIndex = this.commitIndex,
          lastApplied = this.lastApplied,
          leaderId = newLeader)
      } else this

    def applyEvent(event: Event): State
  }

  final case class Follower(
      currentTerm: Long,
      votedFor: Option[String],
      log: Log,
      commitIndex: Long,
      lastApplied: Long,
      leaderId: Option[String])
      extends State {

    override def applyEvent(event: Event): State = {
      event match {
        case EntriesAppended(
              term,
              leaderId,
              prevLogIndex,
              leaderCommit,
              entries) =>
          val newLog = this.log
            .removeConflictingEntries(entries, prevLogIndex.toInt)
            .appendEntries(entries, prevLogIndex.toInt)

          this.copy(
            log = newLog,
            votedFor = if (term > currentTerm) None else this.votedFor,
            commitIndex =
              if (leaderCommit > this.commitIndex)
                (newLog.logEntries.length - 1).min(leaderCommit)
              else this.commitIndex,
            currentTerm = if (term > currentTerm) term else currentTerm,
            leaderId = Some(leaderId))
        case VoteGranted(term, candidateId) =>
          this.copy(
            votedFor = Some(candidateId),
            currentTerm = if (term > currentTerm) term else currentTerm)
        case VoteRejected(term) =>
          this.copy(
            currentTerm = if (term > currentTerm) term else currentTerm,
            votedFor = if (term > currentTerm) None else this.votedFor)
        case other => this.mayConvertToFollower(other.term)
      }
    }
  }

  final case class Leader(
      currentTerm: Long,
      votedFor: Option[String],
      log: Log,
      commitIndex: Long,
      lastApplied: Long,
      nextIndex: Map[String, Long],
      matchIndex: Map[String, Long])
      extends State {

    override def applyEvent(event: Event): State = ???
  }
  object Leader {
    def init(
        currentTerm: Long,
        log: Log,
        commitIndex: Long,
        lastApplied: Long): Leader =
      Leader(
        currentTerm = currentTerm,
        votedFor = None,
        log = log,
        commitIndex = commitIndex,
        lastApplied = lastApplied,
        nextIndex =
          Map.from[String, Long](Configuration.getConfiguration.map(server =>
            (server.id, log.logEntries.length))),
        matchIndex = Map.from[String, Long](
          Configuration.getConfiguration.map(server => (server.id, -1))))
  }

  final case class Candidate(
      currentTerm: Long,
      votedFor: Option[String],
      log: Log,
      commitIndex: Long,
      lastApplied: Long,
      countVotes: Int)
      extends State {
    override def applyEvent(event: Event): State = {
      event match {
        case RequestVoteResponseGranted(_) =>
          val newCountVotes = countVotes + 1
          if (newCountVotes >= Configuration.majority)
            Leader.init(currentTerm, log, commitIndex, lastApplied)
          else this.copy(countVotes = countVotes + 1)
        case e @ EntriesAppended(term, leaderId, _, _, _) =>
          this.mayConvertToFollower(term, Some(leaderId)).applyEvent(e)
        case other =>
          this.mayConvertToFollower(other.term)
      }
    }
  }

  sealed trait Command extends CborSerializable

  private final case object ElectionTimeoutElapsed extends Command
  final case class AppendEntries(
      term: Long,
      leaderId: String,
      prevLogIndex: Long,
      prevLogTerm: Long,
      entries: Log,
      leaderCommit: Long,
      replyTo: ActorRef[StatusReply[AppendEntriesResponse]])
      extends Command

  final case class RequestVote(
      term: Long,
      candidateId: String,
      lastLogIndex: Long,
      lastLogTerm: Long,
      replyTo: ActorRef[StatusReply[RequestVoteResponse]])
      extends Command

  // client requests for leader
  final case class ClientRequest(
      request: TelegraftRequest,
      replyTo: ActorRef[StatusReply[TelegraftResponse]])
      extends Command

  final case class AppendEntriesResponse(term: Long, success: Boolean)
      extends Command

  final case class RequestVoteResponse(term: Long, voteGranted: Boolean)
      extends Command

  sealed trait Event {
    val term: Long
  }

  final case class EntriesAppended(
      term: Long,
      leaderId: String,
      prevLogIndex: Long,
      leaderCommit: Long,
      entries: Log)
      extends Event

  final case class VoteGranted(term: Long, candidateId: String) extends Event
  final case class VoteRejected(term: Long) extends Event
  final case class RequestVoteResponseGranted(term: Long) extends Event
  final case class RequestVoteResponseRejected(term: Long) extends Event

  final case class ClientRequested(term: Long = -1) extends Event
  // end protocol

  private def appendEntriesReceiverImpl(
      currentTerm: Long,
      log: Log,
      term: Long,
      leaderId: String,
      prevLogIndex: Long,
      prevLogTerm: Long,
      entries: Log,
      leaderCommit: Long,
      replyTo: ActorRef[StatusReply[AppendEntriesResponse]])
      : Effect[Event, State] = {
    if (term < currentTerm || log.entryIsConflicting(
        log,
        prevLogIndex.toInt,
        prevLogTerm)) {
      Effect
        .persist(
          EntriesAppended(term, leaderId, prevLogIndex, leaderCommit, entries))
        .thenReply(replyTo)(state =>
          StatusReply.Success(
            AppendEntriesResponse(state.currentTerm, success = false)))
    } else
      Effect
        .persist(
          EntriesAppended(term, leaderId, prevLogIndex, leaderCommit, entries))
        .thenReply(replyTo) { state =>
          StatusReply.Success(
            AppendEntriesResponse(state.currentTerm, success = true))
        }
  }

  private def requestVoteReceiverImpl(
      currentTerm: Long,
      log: Log,
      votedFor: Option[String],
      term: Long,
      candidateId: String,
      lastLogIndex: Long,
      lastLogTerm: Long,
      replyTo: ActorRef[StatusReply[RequestVoteResponse]])
      : Effect[Event, State] = {
    if (term >= currentTerm && (votedFor.isEmpty || votedFor.contains(
        candidateId) && !log.isMoreUpToDate(lastLogIndex.toInt, lastLogTerm))) {
      Effect
        .persist(VoteGranted(term, candidateId))
        .thenReply(replyTo)(state =>
          StatusReply.Success(
            RequestVoteResponse(state.currentTerm, voteGranted = true)))

    } else
      Effect
        .persist(VoteRejected(term))
        .thenReply(replyTo)(state =>
          StatusReply.Success(
            RequestVoteResponse(state.currentTerm, voteGranted = false)))
  }

  private val commandHandler: (State, Command) => Effect[Event, State] =
    (s, c) =>
      s match {
        case Follower(
              currentTerm,
              votedFor,
              log,
              commitIndex,
              lastApplied,
              leaderId) =>
          c match {
            // routes to leader, waits for response and sends it to client
            case ClientRequest(request, replyTo) => ???
            case rpc =>
              commonReceiverImpl(rpc, currentTerm, log, votedFor)
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
            // send request to state machine, wait for response and answer back to client
            case ClientRequest(request, replyTo) => ???
            case rpc =>
              commonReceiverImpl(rpc, currentTerm, log, votedFor)
          }
        case Candidate(
              currentTerm,
              votedFor,
              log,
              commitIndex,
              lastApplied,
              countVotes) =>
          c match {
            // there is no leader yet, thus push back into stack and compute next message
            case ClientRequest(request, replyTo) => ???
            case rpc =>
              commonReceiverImpl(rpc, currentTerm, log, votedFor)
          }
      }

  private val eventHandler: (State, Event) => State = (s, e) => {

    // if RPC contains term t > current term revert back to follower
    // if commitIndex > lastApplied increment last applied and apply log to state machine
    e match {
      case event @ EntriesAppended(
            term,
            leaderId,
            prevLogIndex,
            leaderCommit,
            entries) =>
        s.applyEvent(event)
      case VoteGranted(term, candidateId) => ???
    }
  }

  private def commonReceiverImpl(
      c: Command,
      currentTerm: Long,
      log: Log,
      votedFor: Option[String]): Effect[Event, State] = {
    c match {
      case AppendEntries(
            term,
            leaderId,
            prevLogIndex,
            prevLogTerm,
            entries,
            leaderCommit,
            replyTo) =>
        appendEntriesReceiverImpl(
          currentTerm,
          log,
          term,
          leaderId,
          prevLogIndex,
          prevLogTerm,
          entries,
          leaderCommit,
          replyTo)
      case RequestVote(term, candidateId, lastLogIndex, lastLogTerm, replyTo) =>
        requestVoteReceiverImpl(
          currentTerm,
          log,
          votedFor,
          term,
          candidateId,
          lastLogIndex,
          lastLogTerm,
          replyTo)
      case RequestVoteResponse(term, voteGranted) => ???
      case AppendEntriesResponse(term, success)   => ???
      case _                                      => Effect.unhandled
    }
  }

  def apply(serverId: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(serverId),
      emptyState = Follower(-1, None, Log.empty, -1, -1, None),
      commandHandler = commandHandler,
      eventHandler = eventHandler)
}
