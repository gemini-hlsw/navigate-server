// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.Parallel
import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import clue.FetchClient
import clue.http4s.Http4sHttpBackend
import clue.http4s.Http4sHttpClient
import lucuma.core.enums.Site
import lucuma.core.refined.auto.*
import lucuma.schemas.ObservationDB
import mouse.boolean.*
import navigate.epics.EpicsService
import navigate.model.config.ControlStrategy
import navigate.model.config.NavigateConfiguration
import navigate.server.tcs.*
import org.http4s.AuthScheme
import org.http4s.Credentials
import org.http4s.Headers
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger

case class Systems[F[_]](
  odb:       OdbProxy[F],
  client:    Client[F],
  tcsCommon: TcsBaseController[F],
  tcsSouth:  TcsSouthController[F],
  tcsNorth:  TcsNorthController[F]
)

object Systems {
  def build[F[_]: {Async, Logger, Http4sHttpBackend, Dispatcher, Parallel}](
    site:     Site,
    client:   Client[F],
    conf:     NavigateConfiguration,
    epicsSrv: EpicsService[F]
  ): Resource[F, Systems[F]] = {
    val tops = decodeTops(conf.navigateEngine.tops)

    // These are placeholders.
    def buildOdbProxy: Resource[F, OdbProxy[F]] =
      val odb = for
        given FetchClient[F, ObservationDB] <-
          Http4sHttpClient.of[F, ObservationDB](
            conf.navigateEngine.odb,
            "ODB",
            Headers(
              Authorization(Credentials.Token(AuthScheme.Bearer, conf.lucumaSSO.serviceToken))
            )
          )
        odbCommands                         <-
          if (conf.navigateEngine.odbNotifications)
            OdbProxy.OdbCommandsImpl[F].pure[F]
          else new OdbProxy.DummyOdbCommands[F].pure[F]
      yield OdbProxy[F](odbCommands)
      Resource.eval(odb)

    def buildTcsSouthController: Resource[F, TcsSouthController[F]] =
      if (conf.navigateEngine.systemControl.tcs === ControlStrategy.FullControl)
        for {
          tcs  <- TcsEpicsSystem.build(epicsSrv, tops)
          p1   <- WfsEpicsSystem.build(
                    epicsSrv,
                    "PWFS1",
                    readTop(tops, "pwfs1".refined),
                    "dc:initSigInit.J".refined,
                    "dc:fgDiag6P1.VALH".refined,
                    "dc:fgDiag1P1.VALB".refined
                  )
          p2   <- WfsEpicsSystem.build(
                    epicsSrv,
                    "PWFS2",
                    readTop(tops, "pwfs2".refined),
                    "dc:initSigInit.J".refined,
                    "dc:fgDiag1P2.VALQ".refined,
                    "dc:fgDiag1P2.VALB".refined
                  )
          oi   <- OiwfsEpicsSystem.build(
                    epicsSrv,
                    readTop(tops, "oiwfs".refined),
                    "dc:fgDiag1P2.VALQ".refined,
                    "dc:fgDiag1P2.VALB".refined
                  )
          mcs  <- McsEpicsSystem.build(epicsSrv, readTop(tops, "mc".refined))
          scs  <- ScsEpicsSystem.build(epicsSrv, readTop(tops, "m2".refined))
          crcs <- CrcsEpicsSystem.build(epicsSrv, readTop(tops, "cr".refined))
          ags  <- AgsEpicsSystem.build(epicsSrv, readTop(tops, "ag".refined))
          hr   <- AcquisitionCameraEpicsSystem.build(epicsSrv, readTop(tops, "hrwfs".refined))
          r    <-
            Resource.eval(
              TcsSouthControllerEpics
                .build(EpicsSystems(tcs, p1, p2, oi, mcs, scs, crcs, ags, hr),
                       conf.navigateEngine.ioTimeout
                )
            )
        } yield r
      else
        Resource.eval(TcsSouthControllerSim.build)

    def buildTcsNorthController: Resource[F, TcsNorthController[F]] =
      if (conf.navigateEngine.systemControl.tcs === ControlStrategy.FullControl)
        for {
          tcs  <- TcsEpicsSystem.build(epicsSrv, tops)
          p1   <- WfsEpicsSystem.build(epicsSrv, "PWFS1", readTop(tops, "pwfs1".refined))
          p2   <- WfsEpicsSystem.build(epicsSrv, "PWFS2", readTop(tops, "pwfs2".refined))
          oi   <- OiwfsEpicsSystem.build(
                    epicsSrv,
                    readTop(tops, "oiwfs".refined),
                    "dc:fgDiag1Oi.VALQ".refined,
                    "dc:fgDiag1Oi.VALB".refined
                  )
          mcs  <- McsEpicsSystem.build(epicsSrv, readTop(tops, "mc".refined))
          scs  <- ScsEpicsSystem.build(epicsSrv, readTop(tops, "m2".refined))
          crcs <- CrcsEpicsSystem.build(epicsSrv, readTop(tops, "cr".refined))
          ags  <- AgsEpicsSystem.build(epicsSrv, readTop(tops, "ag".refined))
          hr   <- AcquisitionCameraEpicsSystem.build(epicsSrv, readTop(tops, "hrwfs".refined))
          r    <- Resource.eval(
                    TcsNorthControllerEpics.build(
                      EpicsSystems(tcs, p1, p2, oi, mcs, scs, crcs, ags, hr),
                      conf.navigateEngine.ioTimeout
                    )
                  )
        } yield r
      else
        Resource.eval(TcsNorthControllerSim.build)

    for {
      odb  <- buildOdbProxy
      tcsS <- buildTcsSouthController
      tcsN <- buildTcsNorthController
    } yield Systems[F](odb, client, (site === Site.GS).fold(tcsS, tcsN), tcsS, tcsN)
  }

  private def decodeTops(s: String): Map[String, String] =
    s.split("=|,")
      .grouped(2)
      .collect { case Array(k, v) =>
        k.trim -> v.trim
      }
      .toMap

}
