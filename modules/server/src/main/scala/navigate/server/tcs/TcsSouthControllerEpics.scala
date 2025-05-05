// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Parallel
import cats.effect.Async
import cats.effect.Ref
import cats.syntax.all.*
import navigate.server.ConnectionTimeout
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.FiniteDuration

class TcsSouthControllerEpics[F[_]: {Async, Parallel, Logger}](
  sys:      EpicsSystems[F],
  timeout:  FiniteDuration,
  stateRef: Ref[F, TcsBaseControllerEpics.State]
) extends TcsBaseControllerEpics[F](
      sys,
      timeout,
      stateRef
    )
    with TcsSouthController[F] {

  override def getInstrumentPorts: F[InstrumentPorts] = (for {
    f2F <- sys.ags.status.flamingos2Port
    ghF <- sys.ags.status.ghostPort
    gmF <- sys.ags.status.gmosPort
    gsF <- sys.ags.status.gsaoiPort
  } yield for {
    f2 <- f2F
    gh <- ghF
    gm <- gmF
    gs <- gsF
  } yield InstrumentPorts(
    flamingos2Port = f2,
    ghostPort = gh,
    gmosPort = gm,
    gnirsPort = 0,
    gpiPort = 0,
    gsaoiPort = gs,
    nifsPort = 0,
    niriPort = 0
  )).verifiedRun(ConnectionTimeout)

}

object TcsSouthControllerEpics {

  def build[F[_]: {Async, Parallel, Logger}](
    sys:     EpicsSystems[F],
    timeout: FiniteDuration
  ): F[TcsSouthControllerEpics[F]] =
    Ref
      .of[F, TcsBaseControllerEpics.State](TcsBaseControllerEpics.State.default)
      .map(
        new TcsSouthControllerEpics(sys, timeout, _)
      )

}
