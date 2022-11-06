package com.telegraft.statemachine.projection

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import akka.projection.testkit.scaladsl.{ProjectionTestKit, TestProjection, TestSourceProvider}
import akka.stream.scaladsl.Source
import com.telegraft.statemachine.persistence.PersistentUser
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.{ExecutionContext, Future}

class UserProjectionSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike {

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

      val repository = TestDatabaseRepository.init
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
