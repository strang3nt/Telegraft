// TODO: implement timer, complete client interactions

package com.telegraft.rafktor

import akka.actor.typed.scaladsl.TimerScheduler
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect
}
import akka.persistence.typed.state.RecoveryCompleted
import com.telegraft.rafktor.Log.{
  ChatCreated,
  ChatJoined,
  MessageSent,
  MessagesRetrieved,
  TelegraftRequest,
  TelegraftResponse,
  UserCreated
}
import com.telegraft.statemachine.proto.{
  CreateChatRequest,
  CreateUserRequest,
  GetMessagesRequest,
  GetMessagesResponse,
  JoinChatRequest,
  SendMessageRequest
}

import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.util.{ Failure, Success }

object RaftServer {

  import RaftState.{ Candidate, Follower, Leader }

  private def randomTimeout: FiniteDuration =
    scala.util.Random.between(150, 301).milliseconds

  sealed trait Command extends CborSerializable

  /**
   * Acts both as the command when followers and candidates
   * election timeout is elapsed and when leader should
   * send heartbeats.
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

  private final case class WrappedClientResponse(
      response: TelegraftResponse,
      replyTo: ActorRef[StatusReply[TelegraftResponse]])
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
      : ReplyEffect[Event, RaftState] = {
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
      : ReplyEffect[Event, RaftState] = {
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
      s: RaftState,
      serverId: String,
      context: ActorContext[Command]): ReplyEffect[Event, RaftState] = {
    Effect
      .persist(ElectionTimeoutElapsed(s.currentTerm, serverId))
      .thenRun { state: RaftState =>
        Configuration.getConfiguration
          .map(server =>
            server.raftGrpcClient.requestVote(
              proto.RequestVoteRequest(
                state.currentTerm,
                serverId,
                lastLogIndex = state.log.logEntries.length - 1,
                lastLogTerm = state.log.logEntries.last._2)))
          .foreach(context.pipeToSelf(_) {
            case Success(r) =>
              RaftServer.RequestVoteResponse(r.term, r.granted)
            // failure (timeout or any other type of failure) is a vote rejection
            case Failure(_) =>
              RequestVoteResponse(-1, voteGranted = false)
          })
      }
      .thenNoReply()
  }

  private def commonReceiverImpl(
      serverId: String,
      c: Command,
      currentTerm: Long,
      log: Log,
      votedFor: Option[String]): ReplyEffect[Event, RaftState] = {
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

  private def commandHandler(
      timer: TimerScheduler[Command],
      context: ActorContext[Command],
      serverId: String,
      s: RaftState,
      c: Command): ReplyEffect[Event, RaftState] = {

    s match {
      case Follower(currentTerm, votedFor, log, _, _, leaderId) =>
        c match {
          // routes to leader, waits for response and sends it to client
          case ClientRequest(request, replyTo) =>
            // TODO: very, very ugly, to fix
            leaderId match {
              case Some(id) =>
                val leaderTelegraftClient =
                  Configuration.getServer(id).telegraftGrpcClient

                val instantToTimestamp
                    : java.time.Instant => com.google.protobuf.timestamp.Timestamp =
                  inst =>
                    com.google.protobuf.timestamp.Timestamp
                      .of(inst.getEpochSecond, inst.getNano)

                val timestampToInstant
                    : com.google.protobuf.timestamp.Timestamp => java.time.Instant =
                  timest =>
                    java.time.Instant
                      .ofEpochSecond(timest.seconds, timest.nanos)

                val messageToLogMessage: Option[
                  com.telegraft.statemachine.proto.Message] => Option[
                  Log.Message] = {
                  case Some(msg) =>
                    Some(
                      Log.Message(
                        msg.userId,
                        msg.chatId,
                        msg.content,
                        timestampToInstant(msg.getSentTime)))
                  case None => None
                }

                request match {
                  case Log.CreateUser(userName) =>
                    Effect.none
                      .thenRun { _: RaftState =>
                        context.pipeToSelf(leaderTelegraftClient.createUser(
                          CreateUserRequest(userName))) {
                          case Failure(exception) =>
                            WrappedClientResponse(
                              UserCreated(
                                ok = false,
                                "",
                                Some(exception.getMessage)),
                              replyTo)
                          case Success(value) =>
                            WrappedClientResponse(
                              UserCreated(
                                value.ok,
                                value.userId,
                                value.errorMessage),
                              replyTo)
                        }
                      }
                      .thenNoReply()
                  case Log.SendMessage(userId, chatId, content, timestamp) =>
                    Effect.none
                      .thenRun { _: RaftState =>
                        context.pipeToSelf(
                          leaderTelegraftClient.sendMessage(
                            SendMessageRequest(
                              userId,
                              chatId,
                              content,
                              Some(instantToTimestamp(timestamp))))) {
                          case Failure(exception) =>
                            WrappedClientResponse(
                              MessageSent(
                                ok = false,
                                None,
                                Some(exception.getMessage)),
                              replyTo)
                          case Success(value) =>
                            WrappedClientResponse(
                              MessageSent(
                                value.ok,
                                messageToLogMessage(value.message),
                                value.errorMessage),
                              replyTo)
                        }
                      }
                      .thenNoReply()
                  case Log.CreateChat(userId, chatName, chatDescription) =>
                    Effect.none
                      .thenRun { _: RaftState =>
                        context.pipeToSelf(
                          leaderTelegraftClient.createChat(
                            CreateChatRequest(
                              userId,
                              chatName,
                              chatDescription))) {
                          case Failure(exception) =>
                            WrappedClientResponse(
                              ChatCreated(
                                ok = false,
                                "",
                                Some(exception.getMessage)),
                              replyTo)
                          case Success(value) =>
                            WrappedClientResponse(
                              ChatCreated(
                                value.ok,
                                value.chatId,
                                value.errorMessage),
                              replyTo)
                        }
                      }
                      .thenNoReply()
                  case Log.JoinChat(userId, chatId) =>
                    Effect.none
                      .thenRun { _: RaftState =>
                        context.pipeToSelf(leaderTelegraftClient.joinChat(
                          JoinChatRequest(userId, chatId))) {
                          case Failure(exception) =>
                            WrappedClientResponse(
                              ChatJoined(
                                ok = false,
                                Some(exception.getMessage)),
                              replyTo)
                          case Success(value) =>
                            WrappedClientResponse(
                              ChatJoined(value.ok, value.errorMessage),
                              replyTo)
                        }
                      }
                      .thenNoReply()
                  case Log.GetMessages(userId, timestamp) =>
                    Effect.none
                      .thenRun { _: RaftState =>
                        context.pipeToSelf(
                          leaderTelegraftClient.getMessages(
                            GetMessagesRequest(
                              userId,
                              Some(instantToTimestamp(timestamp))))) {
                          case Failure(exception) =>
                            WrappedClientResponse(
                              MessagesRetrieved(
                                ok = false,
                                Set.empty,
                                Some(exception.getMessage)),
                              replyTo)
                          case Success(value) =>
                            WrappedClientResponse(
                              MessagesRetrieved(
                                value.ok,
                                value.messages
                                  .map(msg =>
                                    messageToLogMessage(Some(msg)).get)
                                  .toSet,
                                value.errorMessage),
                              replyTo)
                        }
                      }
                      .thenNoReply()
                }

              case None => Effect.stash() //TODO: when to unstash???
            }

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
              .thenRun { state: RaftState =>
                Configuration.getConfiguration
                  .map(server =>
                    (
                      server,
                      server.raftGrpcClient.appendEntries(
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
                        RaftServer.AppendEntriesResponse(
                          response.term,
                          server.id,
                          state.log.logEntries.length - 1,
                          response.success)
                      // failure (timeout or any other type of failure) is a AppendEntries success:
                      // it is important that the leader does nothing, he will retry at the next
                      // timeout
                      case Failure(_) =>
                        RaftServer.AppendEntriesResponse(
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

  def apply(serverId: String): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      Behaviors.withTimers { timers =>
        timers.startSingleTimer(ElectionTimeout, randomTimeout)

        EventSourcedBehavior
          .withEnforcedReplies[Command, Event, RaftState](
            persistenceId = PersistenceId.ofUniqueId(serverId),
            emptyState = RaftState.empty,
            commandHandler = (state, event) =>
              commandHandler(timers, ctx, serverId, state, event),
            eventHandler = (state, event) => state.applyEvent(event))
          .receiveSignal { case (_, RecoveryCompleted) =>
            timers.startSingleTimer(ElectionTimeout, randomTimeout)
          }
      }
    }
  }
}
