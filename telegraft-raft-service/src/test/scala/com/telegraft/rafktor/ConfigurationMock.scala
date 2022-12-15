package com.telegraft.rafktor

class ConfigurationMock(var servers: Set[Server]) extends Configuration {

  override def majority: Int = (servers.size / 2) + 1
  override def getConfiguration: Set[Server] = servers
  override def getServer(serverId: String): Server = servers.filter(_.id == serverId).head
}
