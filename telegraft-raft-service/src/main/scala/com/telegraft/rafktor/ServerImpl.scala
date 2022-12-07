package com.telegraft.rafktor

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import com.telegraft.rafktor.proto.{ TelegraftRaftClientServiceClient, TelegraftRaftServiceClient }

class ServerImpl(val host: String, val port: Int)(implicit private val system: ActorSystem[_]) extends Server {

  val id: String = host + ":" + port

  private val grpcClient =
    GrpcClientSettings
      .connectToServiceAt(host, port)
      // Tls set to false means no encryption over HTTP/2
      .withTls(false)

  val raftGrpcClient: TelegraftRaftServiceClient =
    TelegraftRaftServiceClient(grpcClient)

  val raftClientGrpcClient: TelegraftRaftClientServiceClient =
    TelegraftRaftClientServiceClient(grpcClient)

}
