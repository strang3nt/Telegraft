package com.telegraft.rafktor

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, ReplyEffect }
import akka.persistence.typed.state.RecoveryCompleted
import akka.util.Timeout
import com.telegraft.rafktor.Log.{ LogEntryPayLoad, TelegraftRequest, TelegraftResponse }
import com.telegraft.rafktor.proto.{ ClientRequestPayload, TelegraftRaftClientServiceClient }

import scala.collection.parallel.CollectionConverters._
import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.util.{ Failure, Success }

object RaftServer {

  import RaftState.{ Candidate, Follower, Leader }

  private def randomTermTimeout: FiniteDuration =
    scala.util.Random.between(150, 301).milliseconds

  private def heartBeatTimeout: FiniteDuration = 100.millis

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
  final case object ElectionTimeout extends Command
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

  final case class WrappedResponseToClient(
      status: Boolean,
      response: Option[TelegraftResponse],
      logIndex: Int,
      maybeReplyTo: Option[ActorRef[StatusReply[TelegraftResponse]]])
      extends Command

  sealed trait Event extends CborSerializable {
    val term: Long
  }

  final case class AppliedToStateMachine(term: Long) extends Event

  final case class AnsweredToClient(term: Long, logIndex: Int, telegraftResponse: TelegraftResponse) extends Event

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
      ctx: ActorContext[Command],
      serverId: String,
      state: RaftState,
      rpc: AppendEntries,
      timer: TimerScheduler[Command]): ReplyEffect[Event, RaftState] = {

    val success =
      rpc.term >= state.currentTerm && !Log.entryIsConflicting(state.log, rpc.prevLogIndex.toInt, rpc.prevLogTerm)
    Effect
      .persist(EntriesAppended(rpc.term, rpc))
      .thenRun { state: RaftState =>
        state.log.logEntries.zipWithIndex.slice(state.lastApplied.toInt + 1, state.commitIndex.toInt + 1).foreach {
          case (logEntry, i) if logEntry.payload.isInstanceOf[TelegraftRequest] =>
            ctx.self ! ApplyToStateMachine(logEntry.payload.asInstanceOf[TelegraftRequest], i, logEntry.term, None)
          case _ =>
        }
        timer.startSingleTimer(ElectionTimeout, randomTermTimeout)
      }
      .thenReply(rpc.replyTo) { state =>
        StatusReply.Success(AppendEntriesResponse(state.currentTerm, serverId, state.log.lastLogIndex, success))
      }
  }

  private def requestVoteReceiverImpl(
      currentTerm: Long,
      log: Log,
      votedFor: Option[String],
      rpc: RequestVote): ReplyEffect[Event, RaftState] = {
    val voteGranted = rpc.term >= currentTerm && (votedFor.isEmpty || votedFor
      .contains(rpc.candidateId) && !log.isMoreUpToDate(rpc.lastLogIndex.toInt, rpc.lastLogTerm))
    Effect
      .persist(VoteExpressed(rpc.term, rpc.candidateId, voteGranted))
      .thenReply(rpc.replyTo)(state => StatusReply.Success(RequestVoteResponse(state.currentTerm, voteGranted)))
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
        timer.startSingleTimer(ElectionTimeout, randomTermTimeout)
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
        appendEntriesReceiverImpl(ctx, serverId, state, rpc, timer)
      case rpc: RequestVote =>
        requestVoteReceiverImpl(state.currentTerm, state.log, state.votedFor, rpc)
      case WrappedResponseToClient(_, response, index, maybeReplyTo) =>
        ctx.system.log.info("Applied to state machine: " + response)
        maybeReplyTo match {
          case Some(replyTo) =>
            Effect
              .persist(AnsweredToClient(state.currentTerm, index, response.get))
              .thenReply(replyTo)(_ => StatusReply.Success(response.get))
          case None =>
            Effect.persist(AnsweredToClient(state.currentTerm, index, response.get)).thenNoReply()
        }
      case rpc: ApplyToStateMachine =>
        applyToStateMachine(state, ctx, stm, rpc)
      case _ => Effect.noReply
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

  private def sendHeartBeat(
      config: Configuration,
      state: Leader,
      serverId: String,
      context: ActorContext[Command]): Unit = {
    config.getConfiguration.par.foreach { follower =>
      context.pipeToSelf(follower.raftGrpcClient.appendEntries(state.buildAppendEntriesRPC(serverId, follower.id))) {
        case Success(response) =>
          RaftServer.AppendEntriesResponse(response.term, follower.id, state.log.lastLogIndex, response.success)
        case Failure(_) =>
          RaftServer.AppendEntriesResponse(
            state.currentTerm,
            follower.id,
            state.nextIndex(follower.id) - 1,
            success = false)
      }
    }
  }

  /**
   * Checks if the ApplyToStateMachine passed as input is valid, meaning if it does not conflict
   * with current log. If it does not, then it is applied and the answer is
   * passed to the client or ignored if the server is in follower state. Else
   * the client request is ignored entirely.
   *
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

    if (state.commitIndex > state.lastApplied &&
      state.log(state.lastApplied.toInt + 1).term == command.payloadTerm &&
      command.payloadIndex == state.lastApplied + 1 &&
      state.log(state.lastApplied.toInt + 1).payload == command.payload) {

      Effect
        .persist(AppliedToStateMachine(state.currentTerm))
        .thenRun { x: RaftState =>
          ctx.askWithStatus(stateMachine, StateMachine.ClientRequest(command.payload, _)) {
            case Success(value) =>
              ctx.system.log.info("Raft server with state: " + x + "applied " + value + "to state machine")
              WrappedResponseToClient(status = true, Some(value), command.payloadIndex, command.maybeReplyTo)
            case Failure(err) =>
              ctx.system.log.error("Unknown failure: " + err.getMessage)
              WrappedResponseToClient(status = false, None, command.payloadIndex, command.maybeReplyTo)
          }
        }
        .thenNoReply()

    } else if (state.commitIndex == state.lastApplied || state.lastApplied.toInt + 1 < command.payloadIndex) {
      ctx.self ! command
      Effect.noReply
    } else Effect.noReply
  }

  private def commandHandler(
      config: Configuration,
      timer: TimerScheduler[Command],
      context: ActorContext[Command],
      serverId: String,
      stateMachine: ActorRef[StateMachine.Command],
      s: RaftState,
      c: Command): ReplyEffect[Event, RaftState] = {

    implicit val timeout: Timeout =
      Timeout.create(context.system.settings.config.getDuration("telegraft-raft-service.ask-timeout"))

    s match {
      case Follower(_, _, _, _, _, leaderId) =>
        c match {
          // routes to leader, waits for response and sends it to client
          case rpc @ ClientRequest(request, clientRequest, replyTo) =>
            Effect.none
              .thenRun { _: RaftState =>
                leaderId match {
                  case Some(id) if clientRequest.isDefined =>
                    forwardRequestToLeader(
                      context,
                      config.getServer(id).raftClientGrpcClient,
                      request,
                      clientRequest.get,
                      replyTo)
                  case None if clientRequest.isDefined => context.self ! rpc
                  case _                               =>
                }
              }
              .thenNoReply()

          case ElectionTimeout =>
            startElection(config, timer, s, serverId, context)
          case _ =>
            commonReceiverImpl(s, context, stateMachine, serverId, c, timer)
        }
      case Leader(currentTerm, _, _, _, _, _, _) =>
        c match {
          case ElectionTimeout =>
            Effect.none
              .thenRun { state: RaftState =>
                sendHeartBeat(config, state.asInstanceOf[Leader], serverId, context)
                timer.startSingleTimer(ElectionTimeout, heartBeatTimeout)
              }
              .thenNoReply()

          case AppendEntriesResponse(term, receiverId, lastLogEntry, success) =>
            Effect.persist(AppendEntriesResponseEvent(term, receiverId, lastLogEntry, success)).thenNoReply()

          case ClientRequest(_, Some(clientRequest), replyTo)
              if s.log.logEntries.exists(x => x.maybeClientId.contains(clientRequest) && x.maybeResponse.isDefined) =>
            Effect.reply(replyTo)(
              StatusReply.success(
                s.log.logEntries.filter(_.maybeClientId.contains(clientRequest)).head.maybeResponse.get))

          case ClientRequest(
                _,
                Some(clientRequest),
                _
              ) // if client request something that is yet to be applied, just wait
              if s.log.logEntries.exists(x => x.maybeClientId.contains(clientRequest) && x.maybeResponse.isEmpty) =>
            Effect.none.thenNoReply()

          case ClientRequest(request, clientRequest, replyTo) =>
            Effect
              .persist(ClientRequestEvent(currentTerm, request, clientRequest))
              .thenRun { s: RaftState =>
                val leaderState = s.asInstanceOf[Leader]
                config.getConfiguration.par.foreach { follower =>
                  val appendEntries = leaderState.buildAppendEntriesRPC(serverId, follower.id)
                  context.pipeToSelf(follower.raftGrpcClient.appendEntries(appendEntries)) {
                    case Success(response) =>
                      RaftServer.AppendEntriesResponse(response.term, follower.id, s.log.lastLogIndex, response.success)
                    case Failure(_) =>
                      RaftServer.AppendEntriesResponse(s.currentTerm, follower.id, s.log.lastLogIndex, success = false)
                  }
                }
                context.self ! ApplyToStateMachine(request, s.log.lastLogIndex, s.log.lastLogTerm, Some(replyTo))
                timer.startSingleTimer(ElectionTimeout, heartBeatTimeout)
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
            Effect
              .persist(RequestVoteResponseEvent(term, voteGranted))
              .thenRun { state: RaftState =>
                state match {
                  case leader: Leader =>
                    sendHeartBeat(config, leader, serverId = serverId, context = context)
                    timer.startSingleTimer(ElectionTimeout, heartBeatTimeout)
                  case _ =>
                }
              }
              .thenNoReply()
          case rpc: ClientRequest => Effect.none.thenRun { _: RaftState => context.self ! rpc }.thenNoReply()
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
      Behaviors.withTimers { timer =>
        timer.startSingleTimer(ElectionTimeout, randomTermTimeout)
        EventSourcedBehavior
          .withEnforcedReplies[Command, Event, RaftState](
            persistenceId = PersistenceId.ofUniqueId(serverId),
            emptyState = RaftState.empty,
            commandHandler =
              (state, command) => commandHandler(config, timer, ctx, serverId, stateMachine, state, command),
            eventHandler = (state, event) => state.applyEvent(event, config))
          .receiveSignal { case (_, RecoveryCompleted) =>
            timer.startSingleTimer(ElectionTimeout, randomTermTimeout)
          }
      }
    }
  }
}
