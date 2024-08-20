// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import navigate.epics.Channel
import navigate.epics.EpicsService
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.given

case class AgsChannels[F[_]](
  telltale:   TelltaleChannel[F],
  inPosition: Channel[F, Int],
  sfParked:   Channel[F, Int],
  aoParked:   Channel[F, Int],
  p1Parked:   Channel[F, Int],
  p1Follow:   Channel[F, String],
  p2Parked:   Channel[F, Int],
  p2Follow:   Channel[F, String],
  oiParked:   Channel[F, Int],
  oiFollow:   Channel[F, String]
)

object AgsChannels {
  val sysName: String = "AGS"

  def build[F[_]](
    service: EpicsService[F],
    top:     NonEmptyString
  ): Resource[F, AgsChannels[F]] = for {
    t        <- service.getChannel[String](top, "health.VAL").map(TelltaleChannel(sysName, _))
    inPos    <- service.getChannel[Int](top, "inPosition.VAL")
    sfParked <- service.getChannel[Int](top, "sfParked.VAL")
    aoParked <- service.getChannel[Int](top, "aoParked.VAL")
    p1Parked <- service.getChannel[Int](top, "p1:probeParked.VAL")
    p1Follow <- service.getChannel[String](top, "p1:followS.VAL")
    p2Parked <- service.getChannel[Int](top, "p2:probeParked.VAL")
    p2Follow <- service.getChannel[String](top, "p2:followS.VAL")
    oiParked <- service.getChannel[Int](top, "oi:probeParked.VAL")
    oiFollow <- service.getChannel[String](top, "oi:followS.VAL")
  } yield AgsChannels(
    t,
    inPos,
    sfParked,
    aoParked,
    p1Parked,
    p1Follow,
    p2Parked,
    p2Follow,
    oiParked,
    oiFollow
  )
}