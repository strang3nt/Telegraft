package com.telegraft

import akka.actor.typed.ActorRef
import com.telegraft.rafktor.RaftService
import akka.actor.typed.ActorSystem

import scala.concurrent.Future
import akka.pattern.StatusReply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.Done
import akka.http.scaladsl.server.Directives._
import com.telegraft.SMProtocol.ActionPerformed
import com.telegraft.model.{Chat, Message, User}

class Routes(userRegistry: ActorRef[RaftService.Command])(implicit val system: ActorSystem[_]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import JsonFormats._

  def createChat(userId: Long, c: Chat): Future[StatusReply[Done]] = ???
  def createUser(u: User): Future[ActionPerformed] = ???
  def sendMessageTo(userId: Long, receiverId: Long): Future[StatusReply[Done]] = ???
  def joinChat(userId: Long, chatId: Long): Future[StatusReply[Done]] = ???
  def getUserMessages(userId: Long, timestamp: java.time.Instant, messages: Seq[Message]): Nothing = ???

  val route: Route =
    pathPrefix("users") {
      concat(
        pathEnd {
          concat(
            post {
              entity(as[User]) { user =>
                onSuccess(createUser(user)) { performed =>
                  complete((StatusCodes.Created, performed))
                }
              }
            })
        })
    }
}
