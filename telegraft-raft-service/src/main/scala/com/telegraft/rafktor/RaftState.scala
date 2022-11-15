package com.telegraft.rafktor

import com.telegraft.rafktor.RaftServer.{
  AppendEntriesResponseEvent,
  ElectionTimeoutElapsed,
  EntriesAppended,
  Event,
  RequestVoteResponseEvent,
  VoteExpressed
}

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
      (newLog.logEntries.length - 1).min(leaderCommit.toInt)
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

  protected def becomeLeader: Leader =
    Leader(
      currentTerm = this.currentTerm,
      votedFor = None,
      log = this.log,
      commitIndex = this.commitIndex,
      lastApplied = this.lastApplied,
      nextIndex =
        Map.from[String, Long](Configuration.getConfiguration.map(server => (server.id, log.logEntries.length))),
      matchIndex = Map.from[String, Long](Configuration.getConfiguration.map(server => (server.id, -1))))

  def applyEvent(event: Event): RaftState

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

    override def applyEvent(event: Event): RaftState = {
      event match {
        case EntriesAppended(term, leaderId, prevLogIndex, leaderCommit, entries) =>
          val newLog =
            this.log.removeConflictingEntries(entries, prevLogIndex.toInt).appendEntries(entries, prevLogIndex.toInt)

          this.copy(
            log = newLog,
            votedFor = if (term > currentTerm) None else this.votedFor,
            commitIndex = this.updateCommitIndex(leaderCommit, newLog),
            currentTerm = if (term > currentTerm) term else currentTerm,
            leaderId = Some(leaderId))
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
          if (this.currentTerm <= other.term) this.convertToFollower(other.term)
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

    override def applyEvent(event: Event): RaftState = {

//      If there exists an N such that N > commitIndex, a majority of matchIndex[i] ≥ N
//      and log[N].term == currentTerm: set commitIndex = N

      event match {
        case e @ EntriesAppended(term, leaderId, _, _, _) =>
          if (this.currentTerm <= term) {
            this.convertToFollower(term, Some(leaderId)).applyEvent(e)
          } else this
        // TODO: check AppendEntriesResponseEvent case for correctness
        case AppendEntriesResponseEvent(_, serverId, highestLogEntry, success) if success =>
          this.copy(
            nextIndex = nextIndex + (serverId -> (highestLogEntry + 1)),
            matchIndex =
              if (highestLogEntry > this.matchIndex(serverId))
                matchIndex + (serverId -> highestLogEntry)
              else this.matchIndex)
        case AppendEntriesResponseEvent(term, serverId, _, success) if !success =>
          if (term > currentTerm) {
            this.convertToFollower(term, Some(serverId))
          } else { // term <= currentTerm && !success => log inconsistency
            this.copy(nextIndex = nextIndex + (serverId -> (nextIndex(serverId) - 1)))
          }

        case other =>
          if (this.currentTerm <= other.term)
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
    override def applyEvent(event: Event): RaftState = {
      event match {
        case RequestVoteResponseEvent(_, voteGranted) if voteGranted =>
          val newCountVotes = countVotes + 1
          if (newCountVotes >= Configuration.majority)
            this.becomeLeader
          else this.copy(countVotes = countVotes + 1)
        case e @ EntriesAppended(term, leaderId, _, _, _) =>
          if (this.currentTerm <= term) {
            this.convertToFollower(term, Some(leaderId)).applyEvent(e)
          } else this
        case ElectionTimeoutElapsed(_, serverId) => becomeCandidate(serverId)
        case other =>
          if (this.currentTerm <= other.term) this.convertToFollower(other.term)
          else this
      }
    }
  }
}
