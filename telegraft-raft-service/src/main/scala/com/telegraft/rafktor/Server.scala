package com.telegraft.rafktor

import com.telegraft.rafktor.proto.{TelegraftRaftClientServiceClient, TelegraftRaftServiceClient}

/**
 * A server instance is the representation of a Raft node
 * in the cluster. Each server holds its own address
 */
trait Server {
  def host: String
  def port: Int
  def id: String
  def raftGrpcClient: TelegraftRaftServiceClient
  def raftClientGrpcClient: TelegraftRaftClientServiceClient
}
