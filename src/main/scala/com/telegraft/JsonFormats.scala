package com.telegraft

import com.telegraft.model.{Chat, Message, User, UserChat}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import com.telegraft.SMProtocol.{ActionPerformed, Messages}

object JsonFormats extends DefaultJsonProtocol {

  implicit val userJsonFormat: RootJsonFormat[User] = jsonFormat3(User)
  implicit val userChatJsonFormat: RootJsonFormat[UserChat] = jsonFormat2(UserChat)
  implicit val chatJsonFormat: RootJsonFormat[Chat] = jsonFormat2(Chat)
  implicit val messageJsonFormat: RootJsonFormat[Message] = jsonFormat4(Message)

  implicit val messagesJsonFormat: RootJsonFormat[Messages] = jsonFormat2(Messages)
  implicit val actionPerformedJsonFormat: RootJsonFormat[ActionPerformed] = jsonFormat1(ActionPerformed)
}
