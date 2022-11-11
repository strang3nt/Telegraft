package com.telegraft.rafktor

import com.telegraft.rafktor.Log.LogEntryPayLoad

import scala.collection.immutable

final case class Log(logEntries: immutable.Vector[(LogEntryPayLoad, Long)]) {

  def appendEntries(newEntries: Log, prevLogIndex: Int): Log =
    this.copy(logEntries = this.logEntries ++ newEntries.logEntries.drop(
      this.logEntries.length - 1 - prevLogIndex))

  def entryIsConflicting(log: Log, index: Int, term: Long): Boolean =
    log.logEntries(index)._2 != term

  def removeConflictingEntries(newEntries: Log, prevLogIndex: Int): Log = {
    this.copy(logEntries = this.logEntries.zipWithIndex
      .takeWhile {
        case (entry, index) if index > prevLogIndex =>
          !entryIsConflicting(newEntries, index - prevLogIndex - 1, entry._2)
        case _ => true
      }
      .map(_._1))
  }

  def isMoreUpToDate(
      otherLastLogIndex: Int,
      otherLastLogTerm: Long): Boolean = {
    // if this last log term is higher then other log, this log is more up to date
    (this.logEntries.last._2 > otherLastLogTerm) ||
    // else the bigger log is more up to date
    (this.logEntries.last._2 == otherLastLogTerm && this.logEntries.length - 1 > otherLastLogIndex)
  }

}
object Log {

  def empty: Log = Log(Vector.empty[(LogEntryPayLoad, Long)])
  sealed trait LogEntryPayLoad

  sealed trait TelegraftRequest extends LogEntryPayLoad

  final case class CreateUser(userName: String) extends TelegraftRequest

  final case class SendMessage(
      userId: String,
      chatId: String,
      content: String,
      timestamp: java.time.Instant)
      extends TelegraftRequest

  final case class CreateChat(
      userId: String,
      chatName: String,
      chatDescription: String)
      extends TelegraftRequest

  final case class JoinChat(userId: String, chatId: String)
      extends TelegraftRequest

  final case class GetMessages(userId: String, timestamp: java.time.Instant)
      extends TelegraftRequest

  final case class Message(
      userId: String,
      chatId: String,
      content: String,
      sentTime: java.time.Instant)
      extends CborSerializable

  sealed trait TelegraftResponse

  final case class UserCreated(
      ok: Boolean,
      userId: String,
      errMsg: Option[String])
      extends TelegraftResponse

  final case class MessageSent(
      ok: Boolean,
      msg: Option[Message],
      errMsg: Option[String])
      extends TelegraftResponse

  final case class ChatCreated(
      ok: Boolean,
      chatId: String,
      errMsg: Option[String])
      extends TelegraftResponse

  final case class ChatJoined(ok: Boolean, errMsg: Option[String])
      extends TelegraftResponse

  final case class MessagesRetrieved(
      ok: Boolean,
      messages: Set[Message],
      errMsg: Option[String])
      extends TelegraftResponse

}
