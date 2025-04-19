// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import navigate.epics.Channel
import navigate.epics.EpicsService
import navigate.epics.given
import navigate.server.acm.CadDirective

case class OiwfsChannels[F[_]](
  detSigModeSeqDarkDir: Channel[F, CadDirective],
  seqDarkFilename:      Channel[F, String],
  detSigModeSeqDir:     Channel[F, CadDirective],
  z2m2:                 Channel[F, String],
  detSigInitDir:        Channel[F, CadDirective],
  darkFilename:         Channel[F, String]
)

object OiwfsChannels {
  def build[F[_]](
    service: EpicsService[F],
    top:     NonEmptyString
  ): Resource[F, OiwfsChannels[F]] = for {
    dd   <- service.getChannel[CadDirective](top, "dc:detSigModeSeqDark.DIR")
    sfn  <- service.getChannel[String](top, "dc:detSigModeSeqDark.C")
    smd  <- service.getChannel[CadDirective](top, "dc:detSigModeSeq.DIR")
    z2m2 <- service.getChannel[String](top, "dc:detSigModeSeq.P")
    id   <- service.getChannel[CadDirective](top, "dc:detSigInit.DIR")
    fn   <- service.getChannel[String](top, "dc:detSigInit.B")
  } yield OiwfsChannels(
    detSigModeSeqDarkDir = dd,
    seqDarkFilename = sfn,
    detSigModeSeqDir = smd,
    z2m2 = z2m2,
    detSigInitDir = id,
    darkFilename = fn
  )
}
