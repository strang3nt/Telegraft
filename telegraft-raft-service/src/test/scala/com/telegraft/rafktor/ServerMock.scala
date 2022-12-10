package com.telegraft.rafktor

import akka.actor.typed.ActorSystem
import com.telegraft.rafktor.proto.{TelegraftRaftClientServiceClient, TelegraftRaftServiceClient}

class ServerMock(
    val id: String,
    val raftGrpcClient: TelegraftRaftServiceClient,
    val raftClientGrpcClient: TelegraftRaftClientServiceClient)(system: ActorSystem[_])
    extends Server {
  override def port: Int = -1
  override def host: String = ""

}
