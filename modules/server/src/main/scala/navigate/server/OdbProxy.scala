// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.Applicative
import cats.syntax.all.*
import clue.FetchClient
import lucuma.core.enums.SlewStage
import lucuma.core.model.Observation
import lucuma.schemas.ObservationDB
import navigate.queries.ObsQueriesGQL.AddSlewEventMutation
import org.typelevel.log4cats.Logger

import scala.language.implicitConversions

trait OdbProxy[F[_]] {
  def addSlewEvent(
    obsId: Observation.Id,
    stage: SlewStage
  ): F[Unit]
}

sealed trait OdbEventCommands[F[_]] {
  def addSlewEvent(
    obsId: Observation.Id,
    stage: SlewStage
  ): F[Unit]
}

object OdbProxy {
  def apply[F[_]](
    evCmds: OdbEventCommands[F]
  ): OdbProxy[F] =
    new OdbProxy[F] {
      export evCmds.*
    }

  def dummy[F[_]: Applicative]: OdbProxy[F] =
    OdbProxy[F](new DummyOdbCommands[F])

  class DummyOdbCommands[F[_]: Applicative] extends OdbEventCommands[F] {

    override def addSlewEvent(obsId: Observation.Id, stage: SlewStage): F[Unit] =
      Applicative[F].unit

  }

  class OdbCommandsImpl[F[_]: Applicative](using
    L:      Logger[F],
    client: FetchClient[F, ObservationDB]
  ) extends OdbEventCommands[F] {

    override def addSlewEvent(obsId: Observation.Id, stage: SlewStage): F[Unit] =
      L.info(s"Adding slew event for obsId: $obsId, stage: $stage") *>
        AddSlewEventMutation[F]
          .execute(obsId = obsId, stg = stage)
          .void

  }
}
