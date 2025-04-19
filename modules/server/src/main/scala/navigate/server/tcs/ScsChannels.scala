// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import navigate.epics.Channel
import navigate.epics.EpicsService
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.given

case class ScsChannels[F[_]](
  telltale: TelltaleChannel[F],
  follow:   Channel[F, String]
)

object ScsChannels {
  val sysName: String = "SCS"

  def build[F[_]](
    service: EpicsService[F],
    top:     NonEmptyString
  ): Resource[F, ScsChannels[F]] = for {
    t <- service.getChannel[String](top, "health.VAL").map(TelltaleChannel(sysName, _))
    f <- service.getChannel[String](top, "followS.VAL")
  } yield ScsChannels(t, f)
}
