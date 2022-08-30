package com.telegraft

import com.telegraft.rafktor.SMResponse
import com.telegraft.rafktor.SMCommand
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import com.telegraft.model.{ Message, Chat, User }

object SMProtocol {

  sealed trait Command extends SMCommand
  final case class CreateChat(userId: Long, c: Chat, replyTo: ActorRef[ActionPerformed.type]) extends Command
  final case class CreateUser(u: User, replyTo: ActorRef[ActionPerformed.type]) extends Command
  final case class SendMessageTo(userId: Long, receiverId: Long, replyTo: ActorRef[ActionPerformed.type]) extends Command
  final case class JoinChat(userId: Long, chatId: Long, replyTo: ActorRef[ActionPerformed.type]) extends Command
  final case class GetUserMessages(userId: Long, timestamp: java.time.Instant, replyTo: ActorRef[Messages]) extends Command

  final case class ActionPerformed(msg: String) extends SMResponse
  final case class Messages(userId: Long, messages: Seq[Message]) extends SMResponse

  def apply(): Behavior[Command] = ???

}
