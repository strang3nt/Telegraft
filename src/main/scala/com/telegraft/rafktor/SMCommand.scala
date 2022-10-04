package com.telegraft.rafktor

import akka.actor.typed.ActorRef

trait SMCommand

trait SMResponse

trait SMProtocol
object SMProtocol {
  sealed trait Command

  /** Request from RaftNode, to be computed and once finished acknowledged to RaftNode */
  final case class MsgFromRaftSystem(act: SMCommand, replyTo: ActorRef[SMResponse]) extends Command

  /** Wrapped response for Akka's pipeToSelf interaction pattern */
  final case class WrappedResponse(result: SMResponse, replyTo: ActorRef[SMResponse]) extends Command
}