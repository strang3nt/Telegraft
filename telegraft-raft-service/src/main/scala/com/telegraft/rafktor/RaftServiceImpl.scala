package com.telegraft.rafktor

import akka.actor.typed.{ ActorRef, ActorSystem, Scheduler }
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import com.telegraft.rafktor.proto.{
  AppendEntriesRequest,
  AppendEntriesResponse,
  LogEntry,
  RequestVoteRequest,
  RequestVoteResponse,
  TelegraftRaftService
}
import io.grpc.Status

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
      .askWithStatus(
        RaftNode.AppendEntries(
          in.term,
          in.leaderId,
          in.prevLogEntry,
          in.prevLogTerm,
          fromLogEntriesToLog(in.entries),
          in.leaderCommitIndex,
          _))
      .map { r =>
        proto.AppendEntriesResponse(r.term, r.success)
      }
  }

  private def logEntryPayloadTranslator(
      payload: proto.LogEntryPayload): Log.LogEntryPayLoad = {

    import com.telegraft.statemachine.proto.{
      CreateChatRequest,
      CreateUserRequest,
      GetMessagesRequest,
      JoinChatRequest,
      SendMessageRequest
    }

    import com.telegraft.rafktor.proto.LogEntryPayload.Payload.{
      CreateChat,
      CreateUser,
      Empty,
      GetMessage,
      JoinChat,
      SendMessage
    }

    payload.payload match {
      case CreateChat(
            CreateChatRequest(userId, chatName, chatDescription, _)) =>
        Log.CreateChat(userId, chatName, chatDescription)
      case CreateUser(CreateUserRequest(userName, _)) =>
        Log.CreateUser(userName)
      case JoinChat(JoinChatRequest(userId, chatId, _)) =>
        Log.JoinChat(userId, chatId)
      case GetMessage(GetMessagesRequest(userId, messagesAfter, _)) =>
        Log.GetMessages(
          userId,
          java.time.Instant
            .ofEpochSecond(messagesAfter.get.seconds, messagesAfter.get.nanos))
      case SendMessage(
            SendMessageRequest(userId, chatId, content, timestamp, _)) =>
        Log.SendMessage(
          userId,
          chatId,
          content,
          java.time.Instant
            .ofEpochSecond(timestamp.get.seconds, timestamp.get.nanos))
    }
  }

  private def fromLogEntriesToLog(logEntries: Seq[LogEntry]): Log =
    Log(
      logEntries
        .map(l => (logEntryPayloadTranslator(l.getType), l.term))
        .toVector)

  /**
   * RequestVote is the command used by a candidate to ask a Raft peer for a vote in an election.
   */
  override def requestVote(
      in: RequestVoteRequest): Future[RequestVoteResponse] = {
    raftNode
      .askWithStatus(
        RaftNode.RequestVote(
          in.term,
          in.candidateId,
          in.lastLogIndex,
          in.lastLogTerm,
          _))
      .map(r => proto.RequestVoteResponse(r.term, r.voteGranted))
  }
}
