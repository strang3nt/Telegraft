package com.telegraft.rafktor

import akka.actor.typed.scaladsl.TimerScheduler
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, ReplyEffect }
import akka.persistence.typed.state.RecoveryCompleted
import akka.util.Timeout
import com.telegraft.rafktor.Log.{
  ChatCreated,
  ChatJoined,
  LogEntryPayLoad,
  MessageSent,
  MessagesRetrieved,
  TelegraftRequest,
  TelegraftResponse,
  UserCreated
}
import com.telegraft.statemachine.proto.TelegraftStateMachineServiceClient

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.concurrent.impl.Promise
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
  final case class ClientRequest(request: TelegraftRequest, replyTo: ActorRef[StatusReply[TelegraftResponse]])
      extends Command

  final case class AppendEntriesResponse(term: Long, serverId: String, lastLogEntry: Long, success: Boolean)
      extends Command

  final case class RequestVoteResponse(term: Long, voteGranted: Boolean) extends Command

  private final case class WrappedClientResponse(
      response: TelegraftResponse,
      replyTo: ActorRef[StatusReply[TelegraftResponse]])
      extends Command

  private final case class AppendEntriesResponses(responses: Set[AppendEntriesResponse]) extends Command

  private final case object ApplyToStateMachine extends Command

  sealed trait Event extends CborSerializable {
    val term: Long
  }

  final case class AppliedToStateMachine(term: Long, requestsApplied: Int) extends Event
  final case class EntriesAppended(term: Long, leaderId: String, prevLogIndex: Long, leaderCommit: Long, entries: Log)
      extends Event

  final case class VoteExpressed(term: Long, candidateId: String, voteResult: Boolean) extends Event

  final case class ClientRequestEvent(
      term: Long,
      payload: LogEntryPayLoad,
      replyTo: ActorRef[StatusReply[TelegraftResponse]])
      extends Event

  final case class AppendEntriesResponseEvent(term: Long, serverId: String, highestLogEntry: Long, success: Boolean)
      extends Event
  final case class RequestVoteResponseEvent(term: Long, voteGranted: Boolean) extends Event

  final case class ElectionTimeoutElapsed(term: Long, serverId: String) extends Event

  // end protocol

  private def appendEntriesReceiverImpl(
      serverId: String,
      state: RaftState,
      ctx: ActorContext[Command],
      stm: ActorRef[StateMachine.Command],
      rpc: AppendEntries,
      timer: TimerScheduler[Command]): ReplyEffect[Event, RaftState] = {

    val entriesAppended =
      EntriesAppended(rpc.term, rpc.leaderId, rpc.prevLogIndex, rpc.leaderCommit, rpc.entries)
    if (rpc.term < state.currentTerm) {
      Effect
        .persist(entriesAppended)
        .thenReply(rpc.replyTo)(state =>
          StatusReply.Success(
            AppendEntriesResponse(state.currentTerm, serverId, state.log.lastLogIndex, success = false)))
    } else
      Effect
        .persist(entriesAppended)
        .thenRun { _: RaftState =>
          // TODO: also send to self ApplyToStateMachine
          ctx.self ! ApplyToStateMachine
          timer.startSingleTimer(ElectionTimeout, randomTimeout)
        }
        .thenReply(rpc.replyTo) { s =>
          StatusReply.Success(
            AppendEntriesResponse(
              s.currentTerm,
              serverId,
              s.log.lastLogIndex,
              success = !Log.entryIsConflicting(state.log, rpc.prevLogIndex.toInt, rpc.prevLogTerm)))
        }
        .thenUnstashAll()
  }

  private def requestVoteReceiverImpl(
      currentTerm: Long,
      log: Log,
      votedFor: Option[String],
      rpc: RequestVote): ReplyEffect[Event, RaftState] = {
    if (rpc.term >= currentTerm && (votedFor.isEmpty || votedFor
        .contains(rpc.candidateId) && !log.isMoreUpToDate(rpc.lastLogIndex.toInt, rpc.lastLogTerm))) {
      Effect
        .persist(VoteExpressed(rpc.term, rpc.candidateId, voteResult = true))
        .thenReply(rpc.replyTo)(state =>
          StatusReply.Success(RequestVoteResponse(state.currentTerm, voteGranted = true)))

    } else
      Effect
        .persist(VoteExpressed(rpc.term, rpc.candidateId, voteResult = false))
        .thenReply(rpc.replyTo)(state =>
          StatusReply.Success(RequestVoteResponse(state.currentTerm, voteGranted = false)))
  }

  private def startElection(
      config: Configuration,
      timer: TimerScheduler[Command],
      s: RaftState,
      serverId: String,
      context: ActorContext[Command]): ReplyEffect[Event, RaftState] = {
    Effect
      .persist(ElectionTimeoutElapsed(s.currentTerm, serverId))
      .thenRun { state: RaftState =>
        config.getConfiguration
          .map(server =>
            server.raftGrpcClient.requestVote(
              proto.RequestVoteRequest(
                state.currentTerm,
                serverId,
                lastLogIndex = state.log.lastLogIndex,
                lastLogTerm = state.log.lastLogTerm)))
          .foreach(context.pipeToSelf(_) {
            case Success(r) =>
              RaftServer.RequestVoteResponse(r.term, r.granted)
            // failure (timeout or any other type of failure) is a vote rejection
            case Failure(_) =>
              RequestVoteResponse(-1, voteGranted = false)
          })
      }
      .thenRun(_ => timer.startSingleTimer(ElectionTimeout, randomTimeout))
      .thenNoReply()
  }

  private def commonReceiverImpl(
      state: RaftState,
      ctx: ActorContext[Command],
      stm: ActorRef[StateMachine.Command],
      serverId: String,
      c: Command,
      timer: TimerScheduler[Command])(implicit timeout: Timeout): ReplyEffect[Event, RaftState] = {
    c match {
      case rpc: AppendEntries =>
        appendEntriesReceiverImpl(serverId, state, ctx, stm, rpc, timer)
      case rpc: RequestVote =>
        requestVoteReceiverImpl(state.currentTerm, state.log, state.votedFor, rpc)
      case WrappedClientResponse(response, replyTo) =>
        Effect.reply(replyTo)(StatusReply.Success(response))
      case ApplyToStateMachine =>
        applyToStateMachine(state, ctx, stm)
      case _ => Effect.unhandled.thenNoReply()
    }
  }

  private def forwardRequestToLeader(
      context: ActorContext[Command],
      leaderTelegraftClient: TelegraftStateMachineServiceClient,
      request: TelegraftRequest,
      replyTo: ActorRef[StatusReply[TelegraftResponse]]): Unit = {

    val messageToLogMessage: Option[com.telegraft.statemachine.proto.Message] => Option[Log.Message] = {
      case Some(msg) =>
        Some(Log.Message(msg.userId, msg.chatId, msg.content, msg.getSentTime.asJavaInstant))
      case None => None
    }

    request match {
      case r: Log.CreateUser =>
        context.pipeToSelf(leaderTelegraftClient.createUser(r.convertToGRPC.value)) {
          case Failure(exception) =>
            WrappedClientResponse(UserCreated(ok = false, "", Some(exception.getMessage)), replyTo)
          case Success(value) =>
            WrappedClientResponse(UserCreated(value.ok, value.userId, value.errorMessage), replyTo)
        }

      case r: Log.SendMessage =>
        context.pipeToSelf(leaderTelegraftClient.sendMessage(r.convertToGRPC.value)) {
          case Failure(exception) =>
            WrappedClientResponse(MessageSent(ok = false, None, Some(exception.getMessage)), replyTo)
          case Success(value) =>
            WrappedClientResponse(
              MessageSent(value.ok, messageToLogMessage(value.message), value.errorMessage),
              replyTo)
        }

      case r: Log.CreateChat =>
        context.pipeToSelf(leaderTelegraftClient.createChat(r.convertToGRPC.value)) {
          case Failure(exception) =>
            WrappedClientResponse(ChatCreated(ok = false, "", Some(exception.getMessage)), replyTo)
          case Success(value) =>
            WrappedClientResponse(ChatCreated(value.ok, value.chatId, value.errorMessage), replyTo)
        }

      case r: Log.JoinChat =>
        context.pipeToSelf(leaderTelegraftClient.joinChat(r.convertToGRPC.value)) {
          case Failure(exception) =>
            WrappedClientResponse(ChatJoined(ok = false, Some(exception.getMessage)), replyTo)
          case Success(value) =>
            WrappedClientResponse(ChatJoined(value.ok, value.errorMessage), replyTo)
        }

      case r: Log.GetMessages =>
        context.pipeToSelf(leaderTelegraftClient.getMessages(r.convertToGRPC.value)) {
          case Failure(exception) =>
            WrappedClientResponse(MessagesRetrieved(ok = false, Set.empty, Some(exception.getMessage)), replyTo)
          case Success(value) =>
            WrappedClientResponse(
              MessagesRetrieved(
                value.ok,
                value.messages.map(msg => messageToLogMessage(Some(msg)).get).toSet,
                value.errorMessage),
              replyTo)
        }
    }
  }

  def applyToStateMachine(state: RaftState, ctx: ActorContext[Command], stateMachine: ActorRef[StateMachine.Command])(
      implicit timeout: Timeout): ReplyEffect[Event, RaftState] = {
    if (state.commitIndex > state.lastApplied) {
      Effect
        .persist(AppliedToStateMachine(state.currentTerm, state.commitIndex.toInt - state.lastApplied.toInt))
        .thenRun { state: RaftState =>
          state.log.logEntries.slice(state.lastApplied.toInt + 1, state.commitIndex.toInt + 1).foreach {
            case (payload: TelegraftRequest, _, Some(replyTo)) =>
              ctx.askWithStatus(stateMachine, StateMachine.ClientRequest(payload, _)) {
                case Success(value) =>
                  WrappedClientResponse(value, replyTo)
                case Failure(err) =>
                  throw new RuntimeException("Unknown failure: " + err.getMessage)
              }
            case _ =>
          }
        }
        .thenNoReply()
    } else Effect.stash()
  }

  private def commandHandler(
      config: Configuration,
      timer: TimerScheduler[Command],
      context: ActorContext[Command],
      serverId: String,
      stateMachine: ActorRef[StateMachine.Command],
      s: RaftState,
      c: Command): ReplyEffect[Event, RaftState] = {

    implicit val ec: ExecutionContext = context.executionContext
    implicit val timeout: Timeout =
      Timeout.create(context.system.settings.config.getDuration("telegraft-raft-service.ask-timeout"))

    s match {
      case Follower(currentTerm, votedFor, log, _, _, leaderId) =>
        c match {
          // routes to leader, waits for response and sends it to client
          case ClientRequest(request, replyTo) =>
            leaderId match {
              case Some(id) =>
                Effect.none
                  .thenRun { _: RaftState =>
                    forwardRequestToLeader(context, config.getServer(id).telegraftGrpcClient, request, replyTo)
                  }
                  .thenNoReply()
              case None => Effect.stash()
            }
          case ElectionTimeout =>
            startElection(config, timer, s, serverId, context)
          case rpc =>
            commonReceiverImpl(s, context, stateMachine, serverId, c, timer)
        }
      case Leader(currentTerm, votedFor, log, _, lastApplied, nextIndex, _) =>
        Effect.unstashAll()
        c match {
          case ElectionTimeout =>
            // should send empty heartbeats
            Effect
              .persist(ElectionTimeoutElapsed(s.currentTerm, serverId))
              .thenRun { state: RaftState =>
                // TODO: parallelize
                config.getConfiguration.foreach { server =>
                  val future = server.raftGrpcClient.appendEntries(
                    proto.AppendEntriesRequest(
                      state.currentTerm,
                      serverId,
                      state.log.lastLogIndex,
                      state.log.lastLogTerm,
                      Seq.empty,
                      state.commitIndex))
                  context.pipeToSelf(future) {
                    case Success(response) =>
                      RaftServer.AppendEntriesResponse(
                        response.term,
                        server.id,
                        state.log.lastLogIndex,
                        response.success)
                    case Failure(_) =>
                      RaftServer.AppendEntriesResponse(
                        state.currentTerm,
                        server.id,
                        nextIndex(server.id) - 1,
                        success = false)
                  }
                }
              }
              .thenRun { _ =>
                context.self ! ApplyToStateMachine
                timer.startSingleTimer(ElectionTimeout, randomTimeout)
              }
              .thenNoReply()

          case AppendEntriesResponse(term, receiverId, lastLogEntry, success) =>
            Effect
              .persist(AppendEntriesResponseEvent(term, receiverId, lastLogEntry, success))
              .thenRun { state: RaftState =>
                if (state.commitIndex > state.lastApplied) {
                  // TODO: send message apply to state machine
                }

              }
              .thenNoReply()
          case ClientRequest(request, replyTo) =>
            Effect
              .persist(ClientRequestEvent(currentTerm, request, replyTo))
              .thenRun { s: RaftState =>
                val leaderState = s.asInstanceOf[Leader]
                val futureAppendEntries = {
                  // TODO: parallelize
                  config.getConfiguration.foreach { follower =>
                    val future =
                      follower.raftGrpcClient.appendEntries(leaderState.buildAppendEntriesRPC(serverId, follower.id))
                    context.pipeToSelf(future) {
                      case Success(response) =>
                        RaftServer.AppendEntriesResponse(
                          response.term,
                          follower.id,
                          s.log.lastLogIndex,
                          response.success)
                      case Failure(_) =>
                        // TODO: LOG error
                        RaftServer.AppendEntriesResponse(
                          s.currentTerm,
                          follower.id,
                          s.log.lastLogIndex,
                          success = false)
                    }
                  }
                }
              }
              .thenRun { _ =>
                context.self ! ApplyToStateMachine
                timer.startSingleTimer(ElectionTimeout, randomTimeout)
              }
              .thenNoReply()
          case WrappedClientResponse(response, replyTo) =>
            Effect.reply(replyTo)(StatusReply.Success(response))
          case rpc =>
            commonReceiverImpl(s, context, stateMachine, serverId, c, timer)
        }
      case Candidate(currentTerm, votedFor, log, _, _, _) =>
        c match {
          case ElectionTimeout =>
            startElection(config, timer, s, serverId, context)
          case RequestVoteResponse(term, voteGranted) =>
            Effect.persist(RequestVoteResponseEvent(term, voteGranted)).thenNoReply()
          case ClientRequest(_, _) => Effect.stash()
          case rpc =>
            commonReceiverImpl(s, context, stateMachine, serverId, c, timer)
        }
    }
  }

  def apply(
      serverId: String,
      stateMachine: ActorRef[StateMachine.Command],
      config: Configuration): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      Behaviors.withTimers { timers =>
        timers.startSingleTimer(ElectionTimeout, randomTimeout)
        EventSourcedBehavior
          .withEnforcedReplies[Command, Event, RaftState](
            persistenceId = PersistenceId.ofUniqueId(serverId),
            emptyState = RaftState.empty,
            commandHandler =
              (state, command) => commandHandler(config, timers, ctx, serverId, stateMachine, state, command),
            eventHandler = (state, event) => state.applyEvent(event, config))
          .receiveSignal { case (_, RecoveryCompleted) =>
            timers.startSingleTimer(ElectionTimeout, randomTimeout)
          }
      }
    }
  }
}
