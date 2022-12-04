package com.telegraft.rafktor

import com.telegraft.rafktor.RaftServer.{
  AppendEntriesResponseEvent,
  AppliedToStateMachine,
  ClientRequestEvent,
  ElectionTimeoutElapsed,
  EntriesAppended,
  Event,
  RequestVoteResponseEvent,
  VoteExpressed
}
import com.telegraft.rafktor.proto.{ AppendEntriesRequest, LogEntry, LogEntryPayload }

sealed trait RaftState extends CborSerializable {

  import com.telegraft.rafktor.RaftState.{ Candidate, Follower, Leader }

  // Persistent state
  val currentTerm: Long
  val votedFor: Option[String]
  val log: Log

  // Volatile state
  val commitIndex: Long
  val lastApplied: Long

  protected def convertToFollower(newTerm: Long, newLeader: Option[String] = None): RaftState =
    Follower(
      currentTerm = newTerm,
      votedFor = None,
      log = this.log,
      commitIndex = this.commitIndex,
      lastApplied = this.lastApplied,
      leaderId = newLeader)

  protected def updateCommitIndex(leaderCommit: Long, newLog: Log): Long = {
    if (leaderCommit > this.commitIndex)
      (newLog.length - 1).min(leaderCommit.toInt)
    else this.commitIndex
  }

  protected def becomeCandidate(serverId: String): Candidate =
    Candidate(
      currentTerm = this.currentTerm + 1,
      votedFor = Some(serverId),
      log = this.log,
      commitIndex = this.commitIndex,
      lastApplied = this.lastApplied,
      countVotes = 1)

  protected def becomeLeader(config: Configuration): Leader =
    Leader(
      currentTerm = this.currentTerm,
      votedFor = None,
      log = this.log,
      commitIndex = this.commitIndex,
      lastApplied = this.lastApplied,
      nextIndex = Map.from[String, Long](config.getConfiguration.map(server => (server.id, log.length))),
      matchIndex = Map.from[String, Long](config.getConfiguration.map(server => (server.id, -1))))

  def applyEvent(event: Event, config: Configuration): RaftState

}

object RaftState {
  def empty: Follower = Follower(-1, None, Log.empty, -1, -1, None)

  final case class Follower(
      currentTerm: Long,
      votedFor: Option[String],
      log: Log,
      commitIndex: Long,
      lastApplied: Long,
      leaderId: Option[String])
      extends RaftState {

    override def applyEvent(event: Event, config: Configuration): RaftState = {
      event match {
        case AppliedToStateMachine(_, requestsApplied) => this.copy(lastApplied = lastApplied + requestsApplied)
        case EntriesAppended(term, leaderId, prevLogIndex, leaderCommit, entries) =>
          val newLog =
            this.log.removeConflictingEntries(entries, prevLogIndex.toInt).appendEntries(entries, prevLogIndex.toInt)

          this.copy(
            log = newLog,
            commitIndex = this.updateCommitIndex(leaderCommit, newLog),
            currentTerm = if (term > currentTerm) term else currentTerm,
            leaderId = if (term > currentTerm) Some(leaderId) else this.leaderId)
        case VoteExpressed(term, candidateId, voteResult) =>
          if (voteResult)
            this.copy(votedFor = Some(candidateId), currentTerm = if (term > currentTerm) term else currentTerm)
          else
            this.copy(
              currentTerm = if (term > currentTerm) term else currentTerm,
              votedFor = if (term > currentTerm) None else this.votedFor)
        case ElectionTimeoutElapsed(_, serverId) =>
          becomeCandidate(serverId)
        case other =>
          if (this.currentTerm < other.term) this.convertToFollower(other.term)
          else this
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
      extends RaftState {

    def buildAppendEntriesRPC(leaderId: String, followerId: String): AppendEntriesRequest = {
      val prevLogIndex = if (nextIndex(followerId) == 0) -1 else nextIndex(followerId) - 1
      AppendEntriesRequest(
        currentTerm,
        leaderId,
        prevLogIndex,
        log(prevLogIndex.toInt + 1)._2,
        log.logEntries.drop(prevLogIndex.toInt).map { case (payload, term) =>
          LogEntry(term, Some(LogEntryPayload(payload.convertToGRPC)))
        },
        commitIndex)
    }

    /**
     * @return an N such that N > commitIndex, a majority of matchIndex[i] ≥ N
     * and log[N].term == currentTerm else return commitIndex
     */
    private def updateCommitIndex(updatedMatchIndex: Map[String, Long], config: Configuration): Long = {

      val N = log.logEntries.zipWithIndex.drop((commitIndex + 1).toInt).lastIndexWhere { case ((_, term), i) =>
        term <= currentTerm && updatedMatchIndex.count(_._2 >= i) >= config.majority
      }
      if (N > commitIndex) N else commitIndex
    }
    override def applyEvent(event: Event, config: Configuration): RaftState = {
      event match {
        case AppliedToStateMachine(_, requestsApplied) => this.copy(lastApplied = lastApplied + requestsApplied)
        case e @ EntriesAppended(term, leaderId, _, _, _) =>
          if (this.currentTerm <= term) {
            this.convertToFollower(term, Some(leaderId)).applyEvent(e, config)
          } else this
        // TODO: check AppendEntriesResponseEvent case for correctness
        case AppendEntriesResponseEvent(_, serverId, highestLogEntry, success) if success =>
          val updatedMatchIndex =
            if (highestLogEntry > this.matchIndex(serverId))
              matchIndex + (serverId -> highestLogEntry)
            else this.matchIndex

          this.copy(
            nextIndex = nextIndex + (serverId -> (highestLogEntry + 1)),
            matchIndex = updatedMatchIndex,
            commitIndex = updateCommitIndex(updatedMatchIndex, config))
        case AppendEntriesResponseEvent(term, serverId, _, success) if !success =>
          if (term > currentTerm) {
            this.convertToFollower(term, Some(serverId))
          } else { // term <= currentTerm && !success => log inconsistency
            this.copy(nextIndex = nextIndex + (serverId -> (nextIndex(serverId) - 1)))
          }
        case ClientRequestEvent(term, payload, replyTo) =>
          this.copy(log = this.log.appendEntry(term, payload))
        case other =>
          if (this.currentTerm < other.term)
            this.convertToFollower(other.term)
          else this
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
      extends RaftState {
    override def applyEvent(event: Event, config: Configuration): RaftState = {
      event match {
        case RequestVoteResponseEvent(term, voteGranted) if voteGranted && term <= currentTerm =>
          if (countVotes + 1 >= config.majority) {
            this.becomeLeader(config)
          } else { this.copy(countVotes = countVotes + 1) }
        case e @ EntriesAppended(term, leaderId, _, _, _) =>
          if (this.currentTerm <= term) {
            this.convertToFollower(term, Some(leaderId)).applyEvent(e, config)
          } else this
        case ElectionTimeoutElapsed(_, serverId) => becomeCandidate(serverId)
        case other =>
          if (this.currentTerm < other.term) this.convertToFollower(other.term)
          else this
      }
    }
  }
}
