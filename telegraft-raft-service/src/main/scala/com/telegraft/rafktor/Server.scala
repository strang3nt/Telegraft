package com.telegraft.rafktor

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import com.telegraft.rafktor.proto.TelegraftRaftServiceClient

/**
 * A server instance is the representation of a Raft node
 * in the cluster. Each server holds its own address
 */
class Server(host: String, port: Int)(implicit val system: ActorSystem[_]) {

  val id: String = host + ":" + port

  private val grpcClient =
    GrpcClientSettings
      .connectToServiceAt(host, port)
      // Tls set to false means no encryption over HTTP/2
      .withTls(false)

  val raftNodeGprcClient = TelegraftRaftServiceClient(grpcClient)

}
