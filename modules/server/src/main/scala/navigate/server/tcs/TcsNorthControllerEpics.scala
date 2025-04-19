// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Parallel
import cats.effect.Async
import cats.effect.Ref
import cats.syntax.all.*
import navigate.server.ConnectionTimeout

import scala.concurrent.duration.FiniteDuration

class TcsNorthControllerEpics[F[_]: {Async, Parallel}](
  sys:      EpicsSystems[F],
  timeout:  FiniteDuration,
  stateRef: Ref[F, TcsBaseControllerEpics.State]
) extends TcsBaseControllerEpics[F](
      sys,
      timeout,
      stateRef
    )
    with TcsNorthController[F] {

  override def getInstrumentPorts: F[InstrumentPorts] = (for {
    gmF <- sys.ags.status.gmosPort
    gnF <- sys.ags.status.gnirsPort
    gpF <- sys.ags.status.gpiPort
    nfF <- sys.ags.status.nifsPort
    nrF <- sys.ags.status.niriPort
  } yield for {
    gm <- gmF
    gn <- gnF
    gp <- gpF
    nf <- nfF
    nr <- nrF
  } yield InstrumentPorts(
    flamingos2Port = 0,
    ghostPort = 0,
    gmosPort = gm,
    gnirsPort = gn,
    gpiPort = gp,
    gsaoiPort = 0,
    nifsPort = nf,
    niriPort = nr
  )).verifiedRun(ConnectionTimeout)

}

object TcsNorthControllerEpics {

  def build[F[_]: {Async, Parallel}](
    sys:     EpicsSystems[F],
    timeout: FiniteDuration
  ): F[TcsNorthControllerEpics[F]] =
    Ref
      .of[F, TcsBaseControllerEpics.State](TcsBaseControllerEpics.State.default)
      .map(
        new TcsNorthControllerEpics(sys, timeout, _)
      )

}
