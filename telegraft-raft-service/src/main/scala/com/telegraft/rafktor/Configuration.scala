package com.telegraft.rafktor

import akka.actor.typed.ActorSystem

import scala.jdk.CollectionConverters._

object Configuration {
  def apply(system: ActorSystem[_]): Configuration = {

    val configuration = new Configuration

    val newConfig =
      system.settings.config
        .getObject("raft.servers")
        .unwrapped()
        .asScala
        .take(system.settings.config.getInt("raft.number-of-nodes") - 1)
        .map { case (_, address) =>
          val hostAndPort = address.asInstanceOf[java.util.Map[String, AnyRef]].asScala
          val host = hostAndPort("host").toString
          val port = hostAndPort("port").toString.toInt
          host + ":" + port -> (host, port)
        }

    configuration.defaultConfig = configuration.defaultConfig ++ newConfig

    configuration.nodes = configuration.defaultConfig.map { case (_, (host, port)) =>
      new ServerImpl(host, port)(system)
    }.toSet
    configuration
  }
}

/**
 * Holds a collection of Servers, meaning all the nodes participating
 * in the cluster
 */
class Configuration {

  private var defaultConfig: Map[String, (String, Int)] = Map.empty
  private var nodes: Set[Server] = Set.empty

  def getConfiguration: Set[Server] = nodes
  def majority: Int = (nodes.size / 2) + 1

  def getServer(serverId: String): Server =
    nodes.filter(_.id == serverId).head

}
