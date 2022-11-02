package com.telegraft.statemachine.projection

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }
import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import akka.projection.testkit.scaladsl.{
  ProjectionTestKit,
  TestProjection,
  TestSourceProvider
}
import akka.stream.scaladsl.Source
import com.telegraft.statemachine.database.{
  Chat,
  ChatRepository,
  Connection,
  DatabaseRepository,
  Message,
  MessageRepository,
  UserChatRepository,
  UserRepository
}
import com.telegraft.statemachine.persistence.PersistentUser
import org.scalatest.wordspec.AnyWordSpecLike
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile

object UserProjectionSpec {

  // stub out the db layer and simulate new users added
  class TestUserRepository(
      override val dbConfig: DatabaseConfig[PostgresProfile])(
      implicit ex: ExecutionContext)
      extends UserRepository(dbConfig) {

    var users = Map.empty[String, String]

    override def createUser(userId: String, userName: String): DBIO[Done] = {
      users += userId -> userName
      DBIO.successful(Done)
    }
  }
  class TestDatabaseRepository(override val userRepo: TestUserRepository)(
      implicit ex: ExecutionContext)
      extends DatabaseRepository {

    override val chatRepo: ChatRepository = new ChatRepository(
      Connection.dbConfig)
    override val messageRepo: MessageRepository =
      new MessageRepository(Connection.dbConfig, chatRepo, userRepo)
    override val userChatRepo: UserChatRepository =
      new UserChatRepository(Connection.dbConfig, chatRepo, userRepo)

    override def createTables: Future[Unit] = Future.unit

    override def createChatWithUser(
        chatId: String,
        chatName: String,
        userId: String): _root_.slick.jdbc.PostgresProfile.api.DBIO[Done] =
      DBIO.successful(Done)

    override def getUserChats(userId: String): Future[Seq[Chat]] = Future(
      Seq.empty[Chat])

    override def getMessagesAfterTimestamp(
        userId: String,
        timestamp: Instant): Future[Seq[Message]] = Future(Seq.empty[Message])
  }
}

class UserProjectionSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike {
  import UserProjectionSpec.{ TestDatabaseRepository, TestUserRepository }

  private val projectionTestKit = ProjectionTestKit(system)

  private def createEnvelope(
      event: PersistentUser.Event,
      seqNo: Long,
      timestamp: Long = 0L) =
    EventEnvelope(
      Offset.sequence(seqNo),
      "persistenceId",
      seqNo,
      event,
      timestamp)

  private def toAsyncHandler(itemHandler: UserProjectionHandler)(
      implicit
      ec: ExecutionContext): Handler[EventEnvelope[PersistentUser.Event]] =
    eventEnvelope =>
      Future {
        itemHandler.process(eventEnvelope)
        Done
      }

  "The events from the PersistentUser" should {

    import scala.concurrent.ExecutionContext.Implicits.global

    "create a new user by the projection" in {

      val events =
        Source(
          List[EventEnvelope[PersistentUser.Event]](
            createEnvelope(
              PersistentUser.UserCreated("userid_1", "Alessandro"),
              0L),
            createEnvelope(
              PersistentUser.UserCreated("userid_2", "Filippo"),
              1L)))

      val repository =
        new TestDatabaseRepository(new TestUserRepository(Connection.dbConfig))
      val projectionId =
        ProjectionId("persistentUserTest", "users-0")
      val sourceProvider =
        TestSourceProvider[Offset, EventEnvelope[PersistentUser.Event]](
          events,
          extractOffset = env => env.offset)
      val projection =
        TestProjection[Offset, EventEnvelope[PersistentUser.Event]](
          projectionId,
          sourceProvider,
          () =>
            toAsyncHandler(new UserProjectionHandler(repository))(
              system.executionContext))

      projectionTestKit.run(projection) {
        repository.userRepo.users shouldBe Map(
          "userid_1" -> "Alessandro",
          "userid_2" -> "Filippo")
      }
    }
  }

}
