// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import navigate.epics.Channel
import navigate.epics.EpicsService
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.given

case class AgsChannels[F[_]](
  telltale:        TelltaleChannel[F],
  inPosition:      Channel[F, Int],
  sfParked:        Channel[F, Int],
  aoParked:        Channel[F, Int],
  hwParked:        Channel[F, Int],
  p1Parked:        Channel[F, Int],
  p1Follow:        Channel[F, String],
  p2Parked:        Channel[F, Int],
  p2Follow:        Channel[F, String],
  oiParked:        Channel[F, Int],
  oiFollow:        Channel[F, String],
  instrumentPorts: AgsChannels.InstrumentPortChannels[F],
  aoName:          Channel[F, String],
  hwName:          Channel[F, String],
  sfName:          Channel[F, String],
  p1Angles:        AgsChannels.PwfsAnglesChannels[F],
  p2Angles:        AgsChannels.PwfsAnglesChannels[F]
)

object AgsChannels {
  val sysName: String = "AGS"

  case class InstrumentPortChannels[F[_]](
    gmos:  Channel[F, Int],
    gsaoi: Channel[F, Int],
    gpi:   Channel[F, Int],
    f2:    Channel[F, Int],
    niri:  Channel[F, Int],
    gnirs: Channel[F, Int],
    nifs:  Channel[F, Int],
    ghost: Channel[F, Int]
  )

  object InstrumentPortChannels {

    def build[F[_]](
      service: EpicsService[F],
      top:     NonEmptyString
    ): Resource[F, InstrumentPortChannels[F]] = {
      def buildPortCh(name: String): Resource[F, Channel[F, Int]] =
        service.getChannel(top, s"port:$name.VAL")

      for {
        gm <- buildPortCh("gmos")
        gs <- buildPortCh("gsaoi")
        gp <- buildPortCh("gpi")
        f2 <- buildPortCh("f2")
        nr <- buildPortCh("niri")
        gn <- buildPortCh("nirs")
        nf <- buildPortCh("nifs")
        gh <- buildPortCh("ghost")
      } yield InstrumentPortChannels(
        gm,
        gs,
        gp,
        f2,
        nr,
        gn,
        nf,
        gh
      )
    }

  }

  case class PwfsAnglesChannels[F[_]](
    tableAngle: Channel[F, Double],
    armAngle:   Channel[F, Double]
  )

  object PwfsAnglesChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     NonEmptyString,
      name:    String
    ): Resource[F, PwfsAnglesChannels[F]] = for {
      ta <- service.getChannel[Double](top, s"${name}:RT34Pos.VAL")
      aa <- service.getChannel[Double](top, s"${name}:PA34Pos.VAL")
    } yield PwfsAnglesChannels(ta, aa)
  }

  def build[F[_]](
    service: EpicsService[F],
    top:     NonEmptyString
  ): Resource[F, AgsChannels[F]] = for {
    t        <- service.getChannel[String](top, "health.VAL").map(TelltaleChannel(sysName, _))
    inPos    <- service.getChannel[Int](top, "inPosition.VAL")
    sfParked <- service.getChannel[Int](top, "sfParked.VAL")
    aoParked <- service.getChannel[Int](top, "aoParked.VAL")
    hwParked <- service.getChannel[Int](top, "hwParked.VAL")
    p1Parked <- service.getChannel[Int](top, "p1:probeParked.VAL")
    p1Follow <- service.getChannel[String](top, "p1:followS.VAL")
    p2Parked <- service.getChannel[Int](top, "p2:probeParked.VAL")
    p2Follow <- service.getChannel[String](top, "p2:followS.VAL")
    oiParked <- service.getChannel[Int](top, "oi:probeParked.VAL")
    oiFollow <- service.getChannel[String](top, "oi:followS.VAL")
    ports    <- InstrumentPortChannels.build(service, top)
    aoName   <- service.getChannel[String](top, "aoName")
    hwName   <- service.getChannel[String](top, "hwName")
    sfName   <- service.getChannel[String](top, "sfName")
    p1Angles <- PwfsAnglesChannels.build(service, top, "p1")
    p2Angles <- PwfsAnglesChannels.build(service, top, "p2")
  } yield AgsChannels(
    t,
    inPos,
    sfParked,
    aoParked,
    hwParked,
    p1Parked,
    p1Follow,
    p2Parked,
    p2Follow,
    oiParked,
    oiFollow,
    ports,
    aoName,
    hwName,
    sfName,
    p1Angles,
    p2Angles
  )
}
