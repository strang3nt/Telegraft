package com.telegraft.rafktor

import akka.actor.typed.ActorSystem
import com.telegraft.rafktor.proto.TelegraftRaftServiceClient
import com.telegraft.statemachine.proto.TelegraftStateMachineServiceClient

class ServerMock(
    val id: String,
    val raftGrpcClient: TelegraftRaftServiceClient,
    val telegraftGrpcClient: TelegraftStateMachineServiceClient)(implicit system: ActorSystem[_])
    extends Server {
  override def port: Int = -1
  override def host: String = ""

}
