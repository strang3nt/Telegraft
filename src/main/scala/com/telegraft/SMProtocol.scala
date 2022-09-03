package com.telegraft

import akka.actor.typed.Behavior
import com.telegraft.model.{Chat, Message, User}
import com.telegraft.rafktor.{SMCommand, SMResponse}

object SMProtocol {

  def apply(): Behavior[Command] = ???

  sealed trait Command extends SMCommand

  sealed trait Response extends SMResponse

  final case class CreateChat(userId: Long, c: Chat) extends Command

  final case class GetUser(userId: Long) extends Command

  final case class CreateUser(userName: String, password: String) extends Command

  final case class SendMessageTo(userId: Long, receiverId: Long, content: String) extends Command

  final case class JoinChat(userId: Long, chatId: Long) extends Command

  final case class GetUserMessages(userId: Long, timestamp: java.time.Instant) extends Command

  final case class ActionPerformed(msg: String) extends Response

  final case class Messages(userId: Long, messages: Seq[Message]) extends Response

  final case class GetUserResponse(maybeUser: Option[User]) extends Response

}
