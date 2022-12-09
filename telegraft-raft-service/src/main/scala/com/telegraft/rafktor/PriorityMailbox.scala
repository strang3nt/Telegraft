package com.telegraft.rafktor

import akka.actor.ActorSystem
import akka.dispatch.{PriorityGenerator, UnboundedStablePriorityMailbox}
import com.telegraft.rafktor.RaftServer.ElectionTimeout
import com.typesafe.config.Config
class PriorityMailbox(settings: ActorSystem.Settings, config: Config) extends UnboundedStablePriorityMailbox(
  PriorityGenerator {
    case ElectionTimeout => 0
    case _ => 1
  })

