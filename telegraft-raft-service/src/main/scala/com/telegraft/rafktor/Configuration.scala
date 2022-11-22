package com.telegraft.rafktor

import akka.actor.typed.ActorSystem
import scala.jdk.CollectionConverters._

object Configuration {
  def apply(system: ActorSystem[_]): Unit = {

    val configuration = new Configuration

    val newConfig =
      system.settings.config
        .getList("raft")
        .unwrapped()
        .asScala
        .map { x =>
          x.asInstanceOf[java.util.Map[String, AnyRef]].asScala.map {
            case (_, value) =>
              val hostPortMap =
                value.asInstanceOf[java.util.Map[String, AnyRef]].asScala
              val host = hostPortMap("host").toString
              val port = hostPortMap("port").asInstanceOf[Int]
              host + ":" + port -> (host, port)
          }
        }
        .reduceLeft(_ ++ _)
    configuration.defaultConfig = configuration.defaultConfig ++ newConfig

    configuration.nodes = configuration.defaultConfig.map {
      case (_, (host, port)) =>
        new Server(host, port)(system)
    }.toSet

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
