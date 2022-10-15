package com.telegraft.statemachine

import scala.concurrent.Future
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.util.Timeout
import org.slf4j.LoggerFactory

class TelegraftStateMachineImpl(stateMachine: ActorRef[StateMachine.Command])(implicit system: ActorSystem[_]) extends proto.TelegraftStateMachineService {

  import system.executionContext
  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config.getDuration("telegraft-statemachine-service.ask-timeout"))

  def createUser(createUserRequest: proto.CreateUserRequest): Future[proto.CreateUserResponse] = {
    val proto.CreateUserRequest(userName, _) = createUserRequest
    stateMachine
      .ask(StateMachine.CreateUser(userName, _))
      .map(_ => proto.CreateUserResponse(true))
  }

  def sendMessage(sendMessageRequest: proto.SendMessageRequest): Future[proto.SendMessageResponse] = ???
  def createChat(createChatRequest: proto.CreateChatRequest): Future[proto.CreateChatResponse] = ???
  def joinChat(joinChatRequest: proto.JoinChatRequest): Future[proto.JoinChatResponse] = ???
  def getMessages(getMessagesRequest: proto.GetMessagesRequest): Future[proto.GetMessagesResponse] = ???
}
