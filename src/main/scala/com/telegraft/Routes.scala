package com.telegraft

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.telegraft.SMProtocol._

import scala.concurrent.Future

class Routes(sessionActor: ActorRef[SessionActor.Command])(implicit val context: ActorContext[_]) {

  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import io.circe.generic.auto._

  private implicit val system: ActorSystem[Nothing] = context.system
  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("akka.routes.ask-timeout"))

  private val users: Route = pathPrefix("users") {
    concat(
      pathEnd {
        post {
          // #users POST
          entity(as[CreateUser]) { user =>
               onSuccess(createUser(user)) { performed =>
              complete((StatusCodes.Created, performed))
            }
          }
          // #users POST
        }
      },
      path(LongNumber) { userId =>
        get {
          // #user/$userId$
          rejectEmptyResponse {
            onSuccess(getUser(userId)) {
              response => {
                complete(response.maybeUser)
              }
            }
          }
          // #user/$userId$
        }
      }
    )
  }
  private val chats: Route = pathPrefix("chats") {
    pathEnd {
      concat(
        post {
          entity(as[CreateChat]) { chat =>
            onSuccess(createChat(chat)) { getUser =>
              complete(StatusCodes.Created, getUser)
            }
          }
        },
        post {
          entity(as[JoinChat]) { join =>
            onSuccess(joinChat(join)) { performed =>
              complete(StatusCodes.OK, performed)
            }
          }
        }
      )
    }
  }
  private val messages: Route = pathPrefix("messages") {
    pathEnd {
      concat(
        post {
          entity(as[SendMessageTo]) { msg =>
            onSuccess(sendMessageTo(msg)) { performed =>
              complete(StatusCodes.OK, performed)
            }
          }
        },
        get {
          parameters("userId".as[Long], "epochTime".as[java.time.Instant]).as(GetUserMessages) { u =>
            onSuccess(getUserMessages(u)) { messages =>
              complete(messages)
            }
          }
        }
      )
    }
  }

  val route: Route = concat(
    users,
    chats,
    messages
  )

  private def createChat(c: CreateChat): Future[ActionPerformed] =
    sessionActor.ask(SessionActor.MsgForRaftService(c, _)).mapTo[ActionPerformed]

  private def getUser(userId: Long): Future[GetUserResponse] =
    sessionActor.ask(SessionActor.MsgForRaftService(GetUser(userId), _)).mapTo[GetUserResponse]

  private def createUser(u: CreateUser): Future[ActionPerformed] =
    sessionActor.ask(SessionActor.MsgForRaftService(u, _)).mapTo[ActionPerformed]

  private def sendMessageTo(msg: SendMessageTo): Future[ActionPerformed] =
    sessionActor.ask(SessionActor.MsgForRaftService(msg, _)).mapTo[ActionPerformed]

  private def joinChat(j: JoinChat): Future[ActionPerformed] =
    sessionActor.ask(SessionActor.MsgForRaftService(j, _)).mapTo[ActionPerformed]

  private def getUserMessages(u: GetUserMessages): Future[Messages] =
    sessionActor.ask(SessionActor.MsgForRaftService(u, _)).mapTo[Messages]
}
