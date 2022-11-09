package com.telegraft.rafktor

import akka.actor.typed.{ ActorRef, ActorSystem, Scheduler }
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout
import com.telegraft.rafktor.proto.{
  AppendEntriesRequest,
  AppendEntriesResponse,
  RequestVoteRequest,
  RequestVoteResponse,
  TelegraftRaftService
}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContext, Future }

/**
 * @param raftNode The raft node the implementation serves.
 *
 * Transforms the gRPCs requests in commands a RaftNode actor
 * can understand. The actor then provides an answer and RaftServiceImpl
 * forwards it.
 */
class RaftServiceImpl(raftNode: ActorRef[RaftNode.Command])(
    implicit system: ActorSystem[_])
    extends TelegraftRaftService {

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val scheduler: Scheduler = system.scheduler
  private implicit val timeout: Timeout = 3.seconds
  private def getNode(nodeId: com.google.protobuf.ByteString): Server = ???

  /**
   * AppendEntries performs a single append entries request / response.
   */
  override def appendEntries(
      in: AppendEntriesRequest): Future[AppendEntriesResponse] = {
    raftNode
      .ask(
        RaftNode.AppendEntries(
          in.term,
          getNode(in.leader),
          in.prevLogEntry,
          in.prevLogTerm,
          in.entries,
          in.leaderCommitIndex,
          _))
      .map { r =>
        proto.AppendEntriesResponse(r.term, r.success)
      }
  }

  /**
   * RequestVote is the command used by a candidate to ask a Raft peer for a vote in an election.
   */
  override def requestVote(
      in: RequestVoteRequest): Future[RequestVoteResponse] = {
    raftNode
      .ask(
        RaftNode.RequestVote(
          in.term,
          getNode(in.candidate),
          in.lastLogIndex,
          in.lastLogTerm,
          _))
      .map(r => proto.RequestVoteResponse(r.term, r.voteGranted))
  }
}
