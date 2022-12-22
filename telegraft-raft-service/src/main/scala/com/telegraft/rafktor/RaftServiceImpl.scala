package com.telegraft.rafktor

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ ActorRef, ActorSystem, Scheduler }
import akka.util.Timeout
import com.telegraft.rafktor.Log.TelegraftRequest
import com.telegraft.rafktor.proto._

import scala.concurrent.{ ExecutionContext, Future }

/**
 * @param raftNode The raft node the implementation serves.
 *
 * Transforms the gRPCs requests in commands a RaftNode actor
 * can understand. The actor then provides an answer and RaftServiceImpl
 * forwards it.
 */
class RaftServiceImpl(raftNode: ActorRef[RaftServer.Command])(implicit system: ActorSystem[_])
    extends TelegraftRaftService {

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val scheduler: Scheduler = system.scheduler
  private implicit val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("telegraft-raft-service.ask-timeout"))

  /**
   * AppendEntries performs a single append entries request / response.
   */
  override def appendEntries(in: AppendEntriesRequest): Future[AppendEntriesResponse] =
    raftNode
      .askWithStatus(
        RaftServer.AppendEntries(
          in.term,
          in.leaderId,
          in.prevLogEntry,
          in.prevLogTerm,
          fromLogEntriesToLog(in.entries),
          in.leaderCommitIndex,
          _))
      .map(r => proto.AppendEntriesResponse(r.term, r.success))
      .recover(_ => proto.AppendEntriesResponse())

  private def fromLogEntriesToLog(logEntries: Seq[LogEntry]): Log =
    Log(
      logEntries
        .map(l => (TelegraftRequest.convertFromGrpc(l.getType.payload), l.term, l.clientRequest))
        .collect { case (Some(payload), term, Some(clientRequest)) =>
          Log.LogEntry(payload, term, Some((clientRequest.clientId, clientRequest.requestId)), None)
        }
        .toVector)

  /**
   * RequestVote is the command used by a candidate to ask a Raft peer for a vote in an election.
   */
  override def requestVote(in: RequestVoteRequest): Future[RequestVoteResponse] =
    raftNode
      .askWithStatus(RaftServer.RequestVote(in.term, in.candidateId, in.lastLogIndex, in.lastLogTerm, _))
      .map(r => proto.RequestVoteResponse(r.term, r.voteGranted))
      .recover(_ => proto.RequestVoteResponse())
}
