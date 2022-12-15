package com.telegraft.rafktor

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ ActorRef, ActorSystem, Scheduler }
import akka.util.Timeout
import com.telegraft.rafktor.Log.{ TelegraftRequest, TelegraftResponse }
import com.telegraft.rafktor.proto._

import scala.concurrent.{ ExecutionContext, Future }

class RaftClientServiceImpl(raftNode: ActorRef[RaftServer.Command], stateMachine: ActorRef[StateMachine.Command])(
    implicit system: ActorSystem[_])
    extends TelegraftRaftClientService {

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val scheduler: Scheduler = system.scheduler
  implicit private val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("telegraft-raft-service.ask-timeout"))

  override def clientQuery(in: ClientQueryPayload): Future[ClientQueryResponse] = {
    stateMachine
      .askWithStatus[Log.TelegraftResponse](
        StateMachine.ClientRequest(TelegraftRequest.convertFromQueryGrpc(in.payload).get, _))
      .map(r => ClientQueryResponse(status = true, TelegraftResponse.convertToQueryGrpc(r), None))
      .recover(err => ClientQueryResponse(status = false, ClientQueryResponse.Payload.Empty, Some(err.getMessage)))
  }
  override def clientRequest(in: ClientRequestPayload): Future[ClientRequestResponse] = {
    raftNode
      .askWithStatus[Log.TelegraftResponse](
        RaftServer.ClientRequest(
          TelegraftRequest.convertFromGrpc(in.payload.get.payload).get,
          Some((in.clientId, in.requestId)),
          _))
      .map(r => ClientRequestResponse(status = true, Some(LogEntryResponse(TelegraftResponse.convertToGrpc(r))), None))
      .recover(err => ClientRequestResponse(status = false, None, Some(err.getMessage)))
  }
}
