// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.http4s

import cats.syntax.all.*
import cats.effect.{IO, Ref}
import fs2.Stream
import engage.model.EngageEvent
import engage.model.enums.{DomeMode, ShutterMode}
import engage.server.{EngageEngine, OdbProxy, Systems}
import munit.CatsEffectSuite
import squants.Angle
import edu.gemini.grackle.syntax.*
import engage.server.tcs.{FollowStatus, ParkStatus, TcsNorthControllerSim, TcsSouthControllerSim}
import cats.*
import cats.effect.unsafe.implicits.global
import edu.gemini.grackle.ValueMapping
import io.circe.{Decoder, Json}
import munit.Clue.generate

import EngageMappings._

class EngageMappingsTest extends CatsEffectSuite {
  import EngageMappingsTest._

  def extractResult[T: Decoder](j: Json, mutation: String): Option[T] = j.hcursor
    .downField("data")
    .downField(mutation)
    .as[T]
    .toOption

  test("Process mount follow command") {
    for {
      eng <- buildServer
      mp  <- EngageMappings[IO](eng)
      r   <- mp.compileAndRun("mutation { mountFollow(enable: true) {} }")
    } yield assert(
      extractResult[FollowStatus](r, "mountFollow").exists(_ === FollowStatus.Following)
    )

  }

  test("Process mount park command") {
    for {
      eng <- buildServer
      mp  <- EngageMappings[IO](eng)
      r   <- mp.compileAndRun("mutation { mountPark { } }")
    } yield assert(extractResult[ParkStatus](r, "mountPark").exists(_ === ParkStatus.Parked))

  }

}

object EngageMappingsTest {
  def buildServer: IO[EngageEngine[IO]] = new EngageEngine[IO] {
    override val systems: Systems[IO] = Systems(
      OdbProxy.dummy[IO],
      new TcsSouthControllerSim[IO],
      new TcsNorthControllerSim[IO]
    )

    override def eventStream: Stream[IO, EngageEvent] = Stream.empty

    override def mcsPark: IO[Unit] = IO.unit

    override def mcsFollow(enable: Boolean): IO[Unit] = IO.unit

    override def rotStop(useBrakes: Boolean): IO[Unit] = IO.unit

    override def rotPark: IO[Unit] = IO.unit

    override def rotFollow(enable: Boolean): IO[Unit] = IO.unit

    override def rotMove(angle: Angle): IO[Unit] = IO.unit

    override def ecsCarouselMode(
      domeMode:      DomeMode,
      shutterMode:   ShutterMode,
      slitHeight:    Double,
      domeEnable:    Boolean,
      shutterEnable: Boolean
    ): IO[Unit] = IO.unit

    override def ecsVentGatesMove(gateEast: Double, westGate: Double): IO[Unit] = IO.unit
  }.pure[IO]
}
