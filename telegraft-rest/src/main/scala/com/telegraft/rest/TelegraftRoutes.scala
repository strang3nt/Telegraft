package com.telegraft.rest

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.telegraft.statemachine.proto._
import akka.http.scaladsl.model.StatusCodes
import com.google.protobuf.timestamp.Timestamp

class TelegraftRoutes(
    smService: TelegraftStateMachineService,
    raftService: TelegraftStateMachineService)(
    implicit val context: ActorContext[_]) {

  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import scalapb_circe.codec._

  val route: Route = concat(
    pathPrefix("users") {
      pathEnd {
        post {
          // #users POST
          entity(as[CreateUserRequest]) { user =>
            onSuccess(raftService.createUser(user)) { performed =>
              complete((StatusCodes.Created, performed))
            }
          }
          // #users POST
        }
      }
    },
    pathPrefix("chats") {
      pathEnd {
        concat(
          post {
            entity(as[CreateChatRequest]) { chat =>
              onSuccess(raftService.createChat(chat)) { getUser =>
                complete(StatusCodes.Created, getUser)
              }
            }
          },
          post {
            entity(as[JoinChatRequest]) { join =>
              onSuccess(raftService.joinChat(join)) { performed =>
                complete(StatusCodes.OK, performed)
              }
            }
          })
      }
    },
    pathPrefix("messages") {
      pathEnd {
        concat(
          post {
            entity(as[SendMessageRequest]) { msg =>
              onSuccess(raftService.sendMessage(msg)) { performed =>
                complete(StatusCodes.OK, performed)
              }
            }
          },
          get {
            parameters("userId".as[String], "epochTime".as[Timestamp]) {
              (userId, timestamp) =>
                onSuccess(smService.getMessages(
                  GetMessagesRequest(userId, Some(timestamp)))) { messages =>
                  complete(messages)
                }
            }
          })
      }
    })

}
