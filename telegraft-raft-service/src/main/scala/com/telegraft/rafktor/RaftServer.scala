package com.telegraft.rafktor

import akka.actor.typed.scaladsl.TimerScheduler
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, ReplyEffect }
import akka.persistence.typed.state.RecoveryCompleted
import akka.util.Timeout
import com.fasterxml.jackson.annotation.JsonIgnore
import com.telegraft.rafktor.Log.{ LogEntryPayLoad, TelegraftRequest, TelegraftResponse }
import com.telegraft.rafktor.proto.{ ClientRequestPayload, TelegraftRaftClientServiceClient }
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.util.{ Failure, Success }
import scala.collection.parallel.CollectionConverters._

object RaftServer {

  import RaftState.{ Candidate, Follower, Leader }

  @JsonIgnore
  private val logger = LoggerFactory.getLogger("com.telegraft.rafktor.RaftServer")

  private def randomTimeout: FiniteDuration =
    scala.util.Random.between(150, 301).milliseconds

  sealed trait Command extends CborSerializable

  final case class ApplyToStateMachine(
      payload: TelegraftRequest,
      payloadIndex: Int,
      payloadTerm: Long,
      maybeReplyTo: Option[ActorRef[StatusReply[TelegraftResponse]]])
      extends Command

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
      clientRequest: Option[(String, String)],
      replyTo: ActorRef[StatusReply[TelegraftResponse]])
      extends Command

  final case class AppendEntriesResponse(term: Long, serverId: String, lastLogEntry: Long, success: Boolean)
      extends Command

  final case class RequestVoteResponse(term: Long, voteGranted: Boolean) extends Command

  private final case class WrappedResponseToClient(
      status: Boolean,
      response: Option[TelegraftResponse],
      logIndex: Int,
      maybeReplyTo: Option[ActorRef[StatusReply[TelegraftResponse]]])
      extends Command

  sealed trait Event extends CborSerializable {
    val term: Long
  }

  final case class AppliedToStateMachine(term: Long, logIndex: Int, telegraftResponse: TelegraftResponse) extends Event

  final case class EntriesAppended(term: Long, rpc: AppendEntries) extends Event

  final case class VoteExpressed(term: Long, candidateId: String, voteResult: Boolean) extends Event

  final case class ClientRequestEvent(term: Long, payload: LogEntryPayLoad, clientRequest: Option[(String, String)])
      extends Event

  final case class AppendEntriesResponseEvent(term: Long, serverId: String, highestLogEntry: Long, success: Boolean)
      extends Event
  final case class RequestVoteResponseEvent(term: Long, voteGranted: Boolean) extends Event

  final case class ElectionTimeoutElapsed(term: Long, serverId: String) extends Event

  // end protocol

  private def appendEntriesReceiverImpl(
      serverId: String,
      state: RaftState,
      rpc: AppendEntries,
      timer: TimerScheduler[Command]): ReplyEffect[Event, RaftState] = {

    val entriesAppended =
      EntriesAppended(rpc.term, rpc)
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
        .thenRun { _: RaftState => logger.info("Raft server voted for " + rpc.candidateId) }
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
        timer.startSingleTimer(ElectionTimeout, randomTimeout)
      }
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
        appendEntriesReceiverImpl(serverId, state, rpc, timer)
      case rpc: RequestVote =>
        requestVoteReceiverImpl(state.currentTerm, state.log, state.votedFor, rpc)
      case WrappedResponseToClient(_, response, index, maybeReplyTo) =>
        maybeReplyTo match {
          case Some(replyTo) =>
            Effect
              .persist(AppliedToStateMachine(state.currentTerm, index, response.get))
              .thenReply(replyTo)(_ => StatusReply.Success(response.get))
          case None =>
            Effect.persist(AppliedToStateMachine(state.currentTerm, index, response.get)).thenNoReply()
        }
      case rpc: ApplyToStateMachine =>
        applyToStateMachine(state, ctx, stm, rpc)
      case _ => Effect.unhandled.thenNoReply()
    }
  }

  private def forwardRequestToLeader(
      context: ActorContext[Command],
      raftClient: TelegraftRaftClientServiceClient,
      request: TelegraftRequest,
      clientRequest: (String, String),
      replyTo: ActorRef[StatusReply[TelegraftResponse]]): Unit = {

    context.pipeToSelf(raftClient.clientRequest(
      ClientRequestPayload(clientRequest._1, clientRequest._2, Some(proto.LogEntryPayload(request.convertToGrpc()))))) {
      // TODO: Log error
      case Failure(_) =>
        WrappedResponseToClient(status = false, None, -1, Some(replyTo))
      case Success(value) =>
        WrappedResponseToClient(
          status = true,
          TelegraftResponse.convertFromGrpc(value.payload.get.payload),
          -1,
          Some(replyTo))
    }

  }

  /**
   * Checks if the ApplyToStateMachine passed as input is valid, meaning if it does not conflict
   * with current log. If it does not, then it is applied and the answer is
   * passed to the client or ignored if the server is in follower state. Else
   * the client request is ignored entirely.
   *
   * @param state current state of the raft server
   * @param ctx context of RaftServer
   * @param stateMachine an actor which sends commands to the state machine
   * @param command contains data about the client request to apply
   * @param timeout implicit configured timeout
   * @return
   */
  private def applyToStateMachine(
      state: RaftState,
      ctx: ActorContext[Command],
      stateMachine: ActorRef[StateMachine.Command],
      command: ApplyToStateMachine)(implicit timeout: Timeout): ReplyEffect[Event, RaftState] = {
    if (state.commitIndex > state.lastApplied) {
      if (state.log.logEntries.zipWithIndex.exists { case (e, i) =>
          command.payload == e.payload && command.payloadTerm == e.term && command.payloadIndex == i
        }) {
        ctx.askWithStatus(stateMachine, StateMachine.ClientRequest(command.payload, _)) {
          case Success(value) =>
            WrappedResponseToClient(status = true, Some(value), command.payloadIndex, command.maybeReplyTo)
          case Failure(err) =>
            throw new RuntimeException("Unknown failure: " + err.getMessage)
        }
      }
      Effect.noReply
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

    if (s.isInstanceOf[Follower] && s.commitIndex > s.lastApplied) {
      s.log.logEntries.zipWithIndex.slice(s.lastApplied.toInt + 1, s.commitIndex.toInt + 1).foreach {
        case (e, i) if e.payload.isInstanceOf[TelegraftRequest] =>
          context.self ! ApplyToStateMachine(e.payload.asInstanceOf[TelegraftRequest], i, e.term, None)
        case _ =>
      }
    }

    s match {
      case Follower(_, _, _, _, _, leaderId) =>
        c match {
          // routes to leader, waits for response and sends it to client
          case ClientRequest(request, clientRequest, replyTo) =>
            leaderId match {
              case Some(id) if clientRequest.isDefined =>
                Effect.none
                  .thenRun { _: RaftState =>
                    forwardRequestToLeader(
                      context,
                      config.getServer(id).raftClientGrpcClient,
                      request,
                      clientRequest.get,
                      replyTo)
                  }
                  .thenNoReply()
              case None if clientRequest.isDefined => Effect.stash()
              case _                               => Effect.noReply
            }
          case ElectionTimeout =>
            startElection(config, timer, s, serverId, context)
          case _ =>
            commonReceiverImpl(s, context, stateMachine, serverId, c, timer)
        }
      case Leader(currentTerm, _, _, _, _, nextIndex, _) =>
        Effect.unstashAll()
        c match {
          case ElectionTimeout =>
            // should send empty heartbeats
            Effect
              .persist(ElectionTimeoutElapsed(s.currentTerm, serverId))
              .thenRun { state: RaftState =>
                config.getConfiguration.par.foreach { server =>
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
                timer.startSingleTimer(ElectionTimeout, randomTimeout)
              }
              .thenNoReply()

          case AppendEntriesResponse(term, receiverId, lastLogEntry, success) =>
            Effect.persist(AppendEntriesResponseEvent(term, receiverId, lastLogEntry, success)).thenNoReply()
          case ClientRequest(_, Some(clientRequest), replyTo)
              if s.log.logEntries.exists(x => x.maybeClientId.contains(clientRequest) && x.maybeResponse.isDefined) =>
            Effect.reply(replyTo)(
              StatusReply.success(
                s.log.logEntries.filter(_.maybeClientId.contains(clientRequest)).head.maybeResponse.get))
          case ClientRequest(request, clientRequest, replyTo) =>
            Effect
              .persist(ClientRequestEvent(currentTerm, request, clientRequest))
              .thenRun { s: RaftState =>
                val leaderState = s.asInstanceOf[Leader]
                val futures = config.getConfiguration.map(follower =>
                  (
                    follower,
                    follower.raftGrpcClient.appendEntries(leaderState.buildAppendEntriesRPC(serverId, follower.id))))
                futures.par.foreach { case (follower, future) =>
                  context.pipeToSelf(future) {
                    case Success(response) =>
                      RaftServer.AppendEntriesResponse(response.term, follower.id, s.log.lastLogIndex, response.success)
                    case Failure(_) =>
                      // TODO: LOG error
                      RaftServer.AppendEntriesResponse(s.currentTerm, follower.id, s.log.lastLogIndex, success = false)
                  }
                }
                Future
                  .sequence(futures.map(_._2))
                  .onComplete(_ =>
                    context.self ! ApplyToStateMachine(request, s.log.lastLogIndex, s.log.lastLogTerm, Some(replyTo)))
                timer.startSingleTimer(ElectionTimeout, randomTimeout)
              }
              .thenNoReply()
          case rpc =>
            commonReceiverImpl(s, context, stateMachine, serverId, rpc, timer)
        }
      case _: Candidate =>
        c match {
          case ElectionTimeout =>
            startElection(config, timer, s, serverId, context)
          case RequestVoteResponse(term, voteGranted) =>
            Effect.persist(RequestVoteResponseEvent(term, voteGranted)).thenNoReply()
          case _: ClientRequest => Effect.stash()
          case rpc =>
            commonReceiverImpl(s, context, stateMachine, serverId, rpc, timer)
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
        logger.info("Raft server " + serverId + " initialized as " + RaftState.empty)
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
