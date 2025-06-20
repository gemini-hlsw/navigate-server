// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.effect.Resource
import cats.effect.Temporal
import cats.syntax.all.*
import eu.timepit.refined.types.string.NonEmptyString
import navigate.epics.*
import navigate.epics.VerifiedEpics.VerifiedEpics
import navigate.server.tcs.FollowStatus.*

trait CrcsEpicsSystem[F[_]] {
  def getFollowingState: VerifiedEpics[F, F, FollowStatus]
}

object CrcsEpicsSystem {
  private[tcs] def buildSystem[F[_]: Applicative](channels: CrcsChannels[F]): CrcsEpicsSystem[F] =
    new CrcsEpicsSystem[F] {
      override def getFollowingState: VerifiedEpics[F, F, FollowStatus] =
        VerifiedEpics
          .readChannel(channels.telltale, channels.follow)
          .map {
            _.map {
              case "ON" => Following
              case _    => NotFollowing
            }
          }
    }

  def build[F[_]: Temporal](
    service: EpicsService[F],
    top:     NonEmptyString
  ): Resource[F, CrcsEpicsSystem[F]] =
    CrcsChannels.build(service, top).map(buildSystem)
}
