// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import navigate.epics.Channel
import navigate.epics.EpicsService
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.given
import navigate.server.acm.CadDirective

case class WfsChannels[F[_]](
  telltale:         TelltaleChannel[F],
  tipGain:          Channel[F, String],
  tiltGain:         Channel[F, String],
  focusGain:        Channel[F, String],
  reset:            Channel[F, Double],
  gainsDir:         Channel[F, CadDirective],
  flux:             Channel[F, Int],
  centroidDetected: Channel[F, Int]
)

object WfsChannels {
  def build[F[_]](
    service:       EpicsService[F],
    sysName:       String,
    top:           NonEmptyString,
    telltaleName:  NonEmptyString,
    gainResetName: NonEmptyString,
    fluxName:      NonEmptyString,
    centroidName:  NonEmptyString
  ): Resource[F, WfsChannels[F]] =
    for {
      tell   <- service.getChannel[String](top, telltaleName.value).map(TelltaleChannel(sysName, _))
      tipG   <- service.getChannel[String](top, "dc:detSigInitFgGain.A")
      tiltG  <- service.getChannel[String](top, "dc:detSigInitFgGain.B")
      focusG <- service.getChannel[String](top, "dc:detSigInitFgGain.C")
      gDir   <- service.getChannel[CadDirective](top, "dc:detSigInitFgGain.DIR")
      rst    <- service.getChannel[Double](top, gainResetName.value)
      fx     <- service.getChannel[Int](top, fluxName.value)
      ct     <- service.getChannel[Int](top, centroidName.value)
    } yield WfsChannels[F](
      telltale = tell,
      tipGain = tipG,
      tiltGain = tiltG,
      focusGain = focusG,
      reset = rst,
      gainsDir = gDir,
      flux = fx,
      centroidDetected = ct
    )
}
