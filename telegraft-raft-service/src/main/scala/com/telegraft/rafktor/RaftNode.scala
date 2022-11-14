package com.telegraft.rafktor

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect
}
import com.telegraft.rafktor.Log.{ TelegraftRequest, TelegraftResponse }

import scala.concurrent.{ ExecutionContext }
import scala.util.{ Failure, Success }

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

    protected def mayConvertToFollower(
        newTerm: Long,
        newLeader: Option[String] = None): State =
      if (this.currentTerm <= newTerm) {
        Follower(
          currentTerm = newTerm,
          votedFor = None,
          log = this.log,
          commitIndex = this.commitIndex,
          lastApplied = this.lastApplied,
          leaderId = newLeader)
      } else this

    protected def updateCommitIndex(leaderCommit: Long, newLog: Log): Long =
      if (leaderCommit > this.commitIndex)
        (newLog.logEntries.length - 1).min(leaderCommit.toInt)
      else this.commitIndex

    protected def becomeCandidate(serverId: String): Candidate =
      Candidate(
        currentTerm = this.currentTerm + 1,
        votedFor = Some(serverId),
        log = this.log,
        commitIndex = this.commitIndex,
        lastApplied = this.lastApplied,
        countVotes = 1)
    protected def becomeLeader: Leader =
      Leader(
        currentTerm = this.currentTerm,
        votedFor = None,
        log = this.log,
        commitIndex = this.commitIndex,
        lastApplied = this.lastApplied,
        nextIndex =
          Map.from[String, Long](Configuration.getConfiguration.map(server =>
            (server.id, log.logEntries.length))),
        matchIndex = Map.from[String, Long](
          Configuration.getConfiguration.map(server => (server.id, -1))))

    def applyEvent(event: Event): State

  }

  object State {
    def empty: Follower = Follower(-1, None, Log.empty, -1, -1, None)
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
            commitIndex = this.updateCommitIndex(leaderCommit, newLog),
            currentTerm = if (term > currentTerm) term else currentTerm,
            leaderId = Some(leaderId))
        case VoteExpressed(term, candidateId, voteResult) =>
          if (voteResult)
            this.copy(
              votedFor = Some(candidateId),
              currentTerm = if (term > currentTerm) term else currentTerm)
          else
            this.copy(
              currentTerm = if (term > currentTerm) term else currentTerm,
              votedFor = if (term > currentTerm) None else this.votedFor)
        case ElectionTimeoutElapsed(_, serverId) =>
          becomeCandidate(serverId)
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

    override def applyEvent(event: Event): State = {
      event match {
        case e @ EntriesAppended(term, leaderId, _, _, _) =>
          this.mayConvertToFollower(term, Some(leaderId)) match {
            case newState: Follower => newState.applyEvent(e)
            case _                  => this
          }
        // TODO: check AppendEntriesResponseEvent case for correctness
        case AppendEntriesResponseEvent(_, serverId, highestLogEntry, success)
            if success =>
          this.copy(
            nextIndex = nextIndex + (serverId -> (highestLogEntry + 1)),
            matchIndex =
              if (highestLogEntry > this.matchIndex(serverId))
                matchIndex + (serverId -> highestLogEntry)
              else this.matchIndex)
        case AppendEntriesResponseEvent(term, serverId, _, success)
            if !success =>
          if (term > currentTerm) {
            this.mayConvertToFollower(term, Some(serverId))
          } else { // term <= currentTerm && !success => log inconsistency
            this.copy(nextIndex =
              nextIndex + (serverId -> (nextIndex(serverId) - 1)))
          }

        case other => this.mayConvertToFollower(other.term)
      }
    }
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
        case RequestVoteResponseEvent(_, voteGranted) if voteGranted =>
          val newCountVotes = countVotes + 1
          if (newCountVotes >= Configuration.majority)
            this.becomeLeader
          else this.copy(countVotes = countVotes + 1)
        case e @ EntriesAppended(term, leaderId, _, _, _) =>
          this.mayConvertToFollower(term, Some(leaderId)) match {
            case newState: Follower => newState.applyEvent(e)
            case _                  => this
          }
        case other =>
          this.mayConvertToFollower(other.term)
      }
    }
  }

  sealed trait Command extends CborSerializable

  /**
   * Acts both as the command when followers and candidates
   * election timeout is elapsed and when leader should
   * sena heartbeats.
   */
  private final case object ElectionTimeout extends Command
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

  final case class AppendEntriesResponse(
      term: Long,
      serverId: String,
      lastLogEntry: Long,
      success: Boolean)
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

  final case class VoteExpressed(
      term: Long,
      candidateId: String,
      voteResult: Boolean)
      extends Event

  final case class AppendEntriesResponseEvent(
      term: Long,
      serverId: String,
      highestLogEntry: Long,
      success: Boolean)
      extends Event
  final case class RequestVoteResponseEvent(term: Long, voteGranted: Boolean)
      extends Event

  final case class ElectionTimeoutElapsed(term: Long, serverId: String)
      extends Event

  final case class ClientRequested(term: Long = -1) extends Event
  // end protocol

  private def appendEntriesReceiverImpl(
      serverId: String,
      currentTerm: Long,
      log: Log,
      term: Long,
      leaderId: String,
      prevLogIndex: Long,
      prevLogTerm: Long,
      entries: Log,
      leaderCommit: Long,
      replyTo: ActorRef[StatusReply[AppendEntriesResponse]])
      : ReplyEffect[Event, State] = {
    val entriesAppended =
      EntriesAppended(term, leaderId, prevLogIndex, leaderCommit, entries)
    if (term < currentTerm || log.entryIsConflicting(
        log,
        prevLogIndex.toInt,
        prevLogTerm)) {
      Effect
        .persist(entriesAppended)
        .thenReply(replyTo)(state =>
          StatusReply.Success(
            AppendEntriesResponse(
              state.currentTerm,
              serverId,
              state.log.logEntries.length - 1,
              success = false)))
    } else
      Effect.persist(entriesAppended).thenReply(replyTo) { state =>
        StatusReply.Success(
          AppendEntriesResponse(
            state.currentTerm,
            serverId,
            state.log.logEntries.length - 1,
            success = true))
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
      : ReplyEffect[Event, State] = {
    if (term >= currentTerm && (votedFor.isEmpty || votedFor.contains(
        candidateId) && !log.isMoreUpToDate(lastLogIndex.toInt, lastLogTerm))) {
      Effect
        .persist(VoteExpressed(term, candidateId, voteResult = true))
        .thenReply(replyTo)(state =>
          StatusReply.Success(
            RequestVoteResponse(state.currentTerm, voteGranted = true)))

    } else
      Effect
        .persist(VoteExpressed(term, candidateId, voteResult = false))
        .thenReply(replyTo)(state =>
          StatusReply.Success(
            RequestVoteResponse(state.currentTerm, voteGranted = false)))
  }

  private def startElection(
      s: State,
      serverId: String,
      context: ActorContext[Command]): ReplyEffect[Event, State] = {
    Effect
      .persist(ElectionTimeoutElapsed(s.currentTerm, serverId))
      .thenRun { state: State =>
        Configuration.getConfiguration
          .map(server =>
            server.raftNodeGprcClient.requestVote(
              proto.RequestVoteRequest(
                state.currentTerm,
                serverId,
                lastLogIndex = state.log.logEntries.length - 1,
                lastLogTerm = state.log.logEntries.last._2)))
          .foreach(context.pipeToSelf(_) {
            case Success(r) =>
              RaftNode.RequestVoteResponse(r.term, r.granted)
            // failure (timeout or any other type of failure) is a vote rejection
            case Failure(_) =>
              RequestVoteResponse(-1, voteGranted = false)
          })
      }
      .thenNoReply()
  }

  private def commandHandler(
      context: ActorContext[Command],
      serverId: String,
      s: State,
      c: Command): ReplyEffect[Event, State] = {

    s match {
      case Follower(currentTerm, votedFor, log, _, _, _) =>
        c match {
          // routes to leader, waits for response and sends it to client
          case ClientRequest(request, replyTo) => ???
          case ElectionTimeout =>
            startElection(s, serverId, context)
          case rpc =>
            commonReceiverImpl(serverId, rpc, currentTerm, log, votedFor)
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
          case ElectionTimeout                 =>
            // should send empty heartbeats
            Effect
              .persist(ElectionTimeoutElapsed(s.currentTerm, serverId))
              .thenRun { state: State =>
                Configuration.getConfiguration
                  .map(server =>
                    (
                      server,
                      server.raftNodeGprcClient.appendEntries(
                        proto.AppendEntriesRequest(
                          state.currentTerm,
                          serverId,
                          state.log.logEntries.length - 1,
                          state.log.logEntries.last._2,
                          Seq.empty,
                          state.commitIndex))))
                  .foreach { case (server, future) =>
                    context.pipeToSelf(future) {
                      case Success(response) =>
                        RaftNode.AppendEntriesResponse(
                          response.term,
                          server.id,
                          state.log.logEntries.length - 1,
                          response.success)
                      // failure (timeout or any other type of failure) is a AppendEntries success:
                      // it is important that the leader does nothing, he will retry at the next
                      // timeout
                      case Failure(_) =>
                        RaftNode.AppendEntriesResponse(
                          state.currentTerm,
                          server.id,
                          nextIndex(
                            serverId) - 1, //this way the index is not updated
                          success = true)
                    }
                  }
              }
              .thenNoReply()

          case AppendEntriesResponse(term, receiverId, lastLogEntry, success) =>
            Effect
              .persist(
                AppendEntriesResponseEvent(
                  term,
                  receiverId,
                  lastLogEntry,
                  success))
              .thenNoReply()

          case rpc =>
            commonReceiverImpl(serverId, rpc, currentTerm, log, votedFor)
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
          case ElectionTimeout =>
            startElection(s, serverId, context)
          case RequestVoteResponse(term, voteGranted) =>
            Effect
              .persist(RequestVoteResponseEvent(term, voteGranted))
              .thenNoReply()
          case rpc =>
            commonReceiverImpl(serverId, rpc, currentTerm, log, votedFor)
        }
    }
  }

  private def commonReceiverImpl(
      serverId: String,
      c: Command,
      currentTerm: Long,
      log: Log,
      votedFor: Option[String]): ReplyEffect[Event, State] = {
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
          serverId,
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
      case _ => Effect.unhandled.thenNoReply()
    }
  }

  def apply(serverId: String): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(serverId),
        emptyState = State.empty,
        commandHandler =
          (state, event) => commandHandler(ctx, serverId, state, event),
        eventHandler = (state, event) => state.applyEvent(event))
    }
  }
}
