// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.acm

import cats.Applicative
import cats.Parallel
import cats.syntax.all.*
import navigate.epics.EpicsSystem
import navigate.epics.RemoteChannel
import navigate.epics.VerifiedEpics.VerifiedEpics

object ParameterList {
  type ParameterList[F[_]] = List[VerifiedEpics[F, F, Unit]]

  extension [F[_]: {Applicative, Parallel}](l: ParameterList[F]) {
    def compile: VerifiedEpics[F, F, Unit] = new VerifiedEpics[F, F, Unit] {
      override val systems: Map[EpicsSystem.TelltaleChannel[F], Set[RemoteChannel[F]]] =
        l.flatMap(_.systems.toList).groupBy(_._1).view.mapValues(_.flatMap(_._2.toList).toSet).toMap
      override val run: F[Unit]                                                        = l.map(_.run).parSequence.void
    }
  }
}
