package com.telegraft.rafktor

import akka.actor.ActorSystem
import akka.dispatch.{ PriorityGenerator, UnboundedStablePriorityMailbox }
import com.telegraft.rafktor.RaftServer.{
  AppendEntries,
  ApplyToStateMachine,
  ElectionTimeout,
  RequestVoteResponse,
  WrappedResponseToClient
}
import com.typesafe.config.Config
class PriorityMailbox(settings: ActorSystem.Settings, config: Config)
    extends UnboundedStablePriorityMailbox(PriorityGenerator {
//      case _: ElectionTimeout.type | AppendEntries | RequestVoteResponse => 0
//      case _: ApplyToStateMachine                                        => 1
      case _: WrappedResponseToClient => 0
      case _                          => 1
    })
