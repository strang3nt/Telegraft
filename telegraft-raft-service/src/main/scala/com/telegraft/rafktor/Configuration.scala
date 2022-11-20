package com.telegraft.rafktor

import akka.actor.typed.ActorSystem

/**
 * Holds a collection of Servers, meaning all the nodes participating
 * in the cluster
 */
object Configuration {

  private val defaultConfig: Map[String, (String, Int)] = ???

  private var nodes: Set[Server] = Set.empty
  def apply(system: ActorSystem[_]): Unit = {
    this.nodes = defaultConfig.map { case (_, (host, port)) =>
      new Server(host, port)(system)
    }.toSet
  }

  def getConfiguration: Set[Server] = ???
  def majority: Int = (nodes.size / 2) + 1

  def getServer(serverId: String): Server =
    nodes.filter(_.id == serverId).head

}
