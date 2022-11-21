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

  private final case class AppendEntriesResponses(
      responses: Set[AppendEntriesResponse])
      extends Command

  private final case class ApplyToStateMachine(
      payload: TelegraftRequest,
      insertionIndex: Int,
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

  final case class ClientRequestEvent(term: Long, payload: LogEntryPayLoad)
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
      rpc: AppendEntries,
      timer: TimerScheduler[Command]): ReplyEffect[Event, RaftState] = {

    val entriesAppended =
      EntriesAppended(
        rpc.term,
        rpc.leaderId,
        rpc.prevLogIndex,
        rpc.leaderCommit,
        rpc.entries)
    if (rpc.term < currentTerm) {
      Effect
        .persist(entriesAppended)
        .thenReply(rpc.replyTo)(state =>
          StatusReply.Success(
            AppendEntriesResponse(
              state.currentTerm,
              serverId,
              state.log.logEntries.length - 1,
              success = false)))
    } else
      Effect
        .persist(entriesAppended)
        .thenRun { _: RaftState =>
          timer.startSingleTimer(ElectionTimeout, randomTimeout)
        }
        .thenReply(rpc.replyTo) { state =>
          StatusReply.Success(
            AppendEntriesResponse(
              state.currentTerm,
              serverId,
              state.log.logEntries.length - 1,
              success = !log.entryIsConflicting(
                log,
                rpc.prevLogIndex.toInt,
                rpc.prevLogTerm)))
        }
        .thenUnstashAll()
  }

  private def requestVoteReceiverImpl(
      currentTerm: Long,
      log: Log,
      votedFor: Option[String],
      rpc: RequestVote): ReplyEffect[Event, RaftState] = {
    if (rpc.term >= currentTerm && (votedFor.isEmpty || votedFor.contains(
        rpc.candidateId) && !log.isMoreUpToDate(
        rpc.lastLogIndex.toInt,
        rpc.lastLogTerm))) {
      Effect
        .persist(VoteExpressed(rpc.term, rpc.candidateId, voteResult = true))
        .thenReply(rpc.replyTo)(state =>
          StatusReply.Success(
            RequestVoteResponse(state.currentTerm, voteGranted = true)))

    } else
      Effect
        .persist(VoteExpressed(rpc.term, rpc.candidateId, voteResult = false))
        .thenReply(rpc.replyTo)(state =>
          StatusReply.Success(
            RequestVoteResponse(state.currentTerm, voteGranted = false)))
  }

  private def startElection(
      timer: TimerScheduler[Command],
      s: RaftState,
      serverId: String,
      context: ActorContext[Command]): ReplyEffect[Event, RaftState] = {
    Effect
      .persist(ElectionTimeoutElapsed(s.currentTerm, serverId))
      .thenRun { state: RaftState =>
        Configuration.getConfiguration
          .filter(serverId == _.id)
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
      .thenRun(_ => timer.startSingleTimer(ElectionTimeout, randomTimeout))
      .thenNoReply()
  }

  private def commonReceiverImpl(
      serverId: String,
      c: Command,
      currentTerm: Long,
      log: Log,
      votedFor: Option[String],
      timer: TimerScheduler[Command]): ReplyEffect[Event, RaftState] = {
    c match {
      case rpc: AppendEntries =>
        appendEntriesReceiverImpl(serverId, currentTerm, log, rpc, timer)
      case rpc: RequestVote =>
        requestVoteReceiverImpl(currentTerm, log, votedFor, rpc)
      case WrappedClientResponse(response, replyTo) =>
        Effect.reply(replyTo)(StatusReply.Success(response))
      case _ => Effect.unhandled.thenNoReply()
    }
  }

  private def forwardRequestToLeader(
      context: ActorContext[Command],
      leaderTelegraftClient: TelegraftStateMachineServiceClient,
      request: TelegraftRequest,
      replyTo: ActorRef[StatusReply[TelegraftResponse]]): Unit = {

    val messageToLogMessage: Option[
      com.telegraft.statemachine.proto.Message] => Option[Log.Message] = {
      case Some(msg) =>
        Some(
          Log.Message(
            msg.userId,
            msg.chatId,
            msg.content,
            msg.getSentTime.asJavaInstant))
      case None => None
    }

    request match {
      case r: Log.CreateUser =>
        context.pipeToSelf(
          leaderTelegraftClient.createUser(r.convertToGRPC.value)) {
          case Failure(exception) =>
            WrappedClientResponse(
              UserCreated(ok = false, "", Some(exception.getMessage)),
              replyTo)
          case Success(value) =>
            WrappedClientResponse(
              UserCreated(value.ok, value.userId, value.errorMessage),
              replyTo)
        }

      case r: Log.SendMessage =>
        context.pipeToSelf(
          leaderTelegraftClient.sendMessage(r.convertToGRPC.value)) {
          case Failure(exception) =>
            WrappedClientResponse(
              MessageSent(ok = false, None, Some(exception.getMessage)),
              replyTo)
          case Success(value) =>
            WrappedClientResponse(
              MessageSent(
                value.ok,
                messageToLogMessage(value.message),
                value.errorMessage),
              replyTo)
        }

      case r: Log.CreateChat =>
        context.pipeToSelf(
          leaderTelegraftClient.createChat(r.convertToGRPC.value)) {
          case Failure(exception) =>
            WrappedClientResponse(
              ChatCreated(ok = false, "", Some(exception.getMessage)),
              replyTo)
          case Success(value) =>
            WrappedClientResponse(
              ChatCreated(value.ok, value.chatId, value.errorMessage),
              replyTo)
        }

      case r: Log.JoinChat =>
        context.pipeToSelf(
          leaderTelegraftClient.joinChat(r.convertToGRPC.value)) {
          case Failure(exception) =>
            WrappedClientResponse(
              ChatJoined(ok = false, Some(exception.getMessage)),
              replyTo)
          case Success(value) =>
            WrappedClientResponse(
              ChatJoined(value.ok, value.errorMessage),
              replyTo)
        }

      case r: Log.GetMessages =>
        context.pipeToSelf(
          leaderTelegraftClient.getMessages(r.convertToGRPC.value)) {
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
                  .map(msg => messageToLogMessage(Some(msg)).get)
                  .toSet,
                value.errorMessage),
              replyTo)
        }
    }
  }

  private def commandHandler(
      timer: TimerScheduler[Command],
      context: ActorContext[Command],
      serverId: String,
      stateMachine: ActorRef[StateMachine.Command],
      s: RaftState,
      c: Command): ReplyEffect[Event, RaftState] = {

    implicit val ec: ExecutionContext = context.executionContext
    implicit val timeout: Timeout = Timeout.create(
      context.system.settings.config
        .getDuration("telegraft-raft-service.ask-timeout"))

    s match {
      case Follower(currentTerm, votedFor, log, _, _, leaderId) =>
        c match {
          // routes to leader, waits for response and sends it to client
          case ClientRequest(request, replyTo) =>
            leaderId match {
              case Some(id) =>
                Effect.none
                  .thenRun { _: RaftState =>
                    forwardRequestToLeader(
                      context,
                      Configuration.getServer(id).telegraftGrpcClient,
                      request,
                      replyTo)
                  }
                  .thenNoReply()
              case None => Effect.stash()
            }
          case ElectionTimeout =>
            startElection(timer, s, serverId, context)
          case rpc =>
            commonReceiverImpl(serverId, rpc, currentTerm, log, votedFor, timer)
        }
      case Leader(currentTerm, votedFor, log, _, lastApplied, nextIndex, _) =>
        Effect.unstashAll()

        c match {

          case ElectionTimeout =>
            // should send empty heartbeats
            Effect
              .persist(ElectionTimeoutElapsed(s.currentTerm, serverId))
              .thenRun { state: RaftState =>
                Configuration.getConfiguration
                  .filter(serverId == _.id)
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

                      case Failure(_) =>
                        RaftServer.AppendEntriesResponse(
                          state.currentTerm,
                          server.id,
                          nextIndex(server.id) - 1,
                          success = false)
                    }
                  }
              }
              .thenRun(_ =>
                timer.startSingleTimer(ElectionTimeout, randomTimeout))
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
          case ClientRequest(request, replyTo) =>
            Effect
              .persist(ClientRequestEvent(currentTerm, request))
              .thenRun { s: RaftState =>
                val leaderState = s.asInstanceOf[Leader]
                val futureAppendEntries =
                  Configuration.getConfiguration.filter(serverId == _.id).map {
                    follower =>
                      (
                        follower,
                        follower.raftGrpcClient
                          .appendEntries(leaderState
                            .buildAppendEntriesRPC(serverId, follower.id))
                          .map(response =>
                            RaftServer.AppendEntriesResponse(
                              response.term,
                              follower.id,
                              s.log.logEntries.length - 1,
                              response.success))
                          .recover { case _ =>
                            RaftServer.AppendEntriesResponse(
                              s.currentTerm,
                              follower.id,
                              nextIndex(follower.id) - 1,
                              success = false)
                          })
                  }

                futureAppendEntries.foreach { case (server, future) =>
                  context.pipeToSelf(future) {
                    case Success(response) => response
                    case Failure(_) =>
                      throw new RuntimeException(
                        s"Server $serverId failed to receive ${server.id}'s answer.'")
                  }
                }

                val appendEntriesFuture =
                  Future.sequence(futureAppendEntries.map(_._2))

                context.pipeToSelf(appendEntriesFuture) {
                  case Success(responses) =>
                    RaftServer.AppendEntriesResponses(responses)
                  case Failure(_) =>
                    RaftServer.AppendEntriesResponses(Set.empty)
                }

                appendEntriesFuture.onComplete {
                  case Success(responses)
                      if responses.count(_.success) >= Configuration.majority =>
                    context.self ! ApplyToStateMachine(
                      request,
                      log.logEntries.length - 1,
                      replyTo)
                  case _ => /* Do nothing, next timers will take care of the rest */
                }
              }
              .thenNoReply()
          case AppendEntriesResponses(responses) =>
            val events = responses.map { r =>
              AppendEntriesResponseEvent(
                r.term,
                r.serverId,
                r.lastLogEntry,
                r.success)
            }
            Effect.persist(events.toSeq).thenNoReply()
          case ApplyToStateMachine(payload, insertionIndex, replyTo) =>
            if (lastApplied > insertionIndex) {
              Effect.none
                .thenRun { _: RaftState =>
                  context.askWithStatus(
                    stateMachine,
                    StateMachine.ClientRequest(payload, _)) {
                    case Success(value) =>
                      WrappedClientResponse(value, replyTo)
                    case Failure(_) =>
                      throw new RuntimeException(
                        "Request should have been a success anyway")
                  }
                }
                .thenNoReply()
            } else Effect.stash()
          case WrappedClientResponse(response, replyTo) =>
            Effect.reply(replyTo)(StatusReply.Success(response))
          case rpc =>
            commonReceiverImpl(serverId, rpc, currentTerm, log, votedFor, timer)
        }
      case Candidate(currentTerm, votedFor, log, _, _, _) =>
        c match {
          case ElectionTimeout =>
            startElection(timer, s, serverId, context)
          case RequestVoteResponse(term, voteGranted) =>
            Effect
              .persist(RequestVoteResponseEvent(term, voteGranted))
              .thenNoReply()
          case ClientRequest(_, _) => Effect.stash()
          case rpc =>
            commonReceiverImpl(serverId, rpc, currentTerm, log, votedFor, timer)
        }
    }
  }

  def apply(
      serverId: String,
      stateMachine: ActorRef[StateMachine.Command]): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      Behaviors.withTimers { timers =>
        timers.startSingleTimer(ElectionTimeout, randomTimeout)
        EventSourcedBehavior
          .withEnforcedReplies[Command, Event, RaftState](
            persistenceId = PersistenceId.ofUniqueId(serverId),
            emptyState = RaftState.empty,
            commandHandler = (state, event) =>
              commandHandler(timers, ctx, serverId, stateMachine, state, event),
            eventHandler = (state, event) => state.applyEvent(event))
          .receiveSignal { case (_, RecoveryCompleted) =>
            timers.startSingleTimer(ElectionTimeout, randomTimeout)
          }
      }
    }
  }
}
