package com.telegraft.rafktor

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.Source
import com.telegraft.rafktor.proto.{
  AppendEntriesPipelineResponse,
  AppendEntriesRequest,
  AppendEntriesResponse,
  RequestVoteRequest,
  RequestVoteResponse,
  TelegraftRaftService
}

import scala.concurrent.Future

/**
 * @param raftNode The raft node the implementation serves.
 *
 * Transforms the gRPCs requests in commands a RaftNode actor
 * can understand. The actor then provides an answer and RaftServiceImpl
 * forwards it.
 */
class RaftServiceImpl(raftNode: ActorRef[RaftNode.Command])
    extends TelegraftRaftService {

  /**
   * AppendEntriesPipeline opens an AppendEntries message stream.
   */
  override def appendEntriesPipeline(in: Source[AppendEntriesRequest, NotUsed])
      : Source[AppendEntriesPipelineResponse, NotUsed] = ???

  /**
   * AppendEntries performs a single append entries request / response.
   */
  override def appendEntries(
      in: AppendEntriesRequest): Future[AppendEntriesResponse] = ???

  /**
   * RequestVote is the command used by a candidate to ask a Raft peer for a vote in an election.
   */
  override def requestVote(
      in: RequestVoteRequest): Future[RequestVoteResponse] = ???
}
