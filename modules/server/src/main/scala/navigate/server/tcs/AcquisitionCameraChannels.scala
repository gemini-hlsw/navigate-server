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
import navigate.server.acm.CarState

case class AcquisitionCameraChannels[F[_]](
  telltale:          TelltaleChannel[F],
  filterReadout:     Channel[F, String],
  lens:              Channel[F, String],
  ndFilter:          Channel[F, String],
  filter:            Channel[F, String],
  frameCount:        Channel[F, String],
  expTime:           Channel[F, String],
  output:            Channel[F, String],
  directory:         Channel[F, String],
  fileName:          Channel[F, String],
  simFile:           Channel[F, String],
  dhsStream:         Channel[F, String],
  dhsOption:         Channel[F, String],
  obsType:           Channel[F, String],
  binning:           Channel[F, String],
  windowing:         Channel[F, String],
  centerX:           Channel[F, String],
  centerY:           Channel[F, String],
  width:             Channel[F, String],
  height:            Channel[F, String],
  dhsLabel:          Channel[F, String],
  stopDir:           Channel[F, CadDirective],
  observeInProgress: Channel[F, CarState]
)

object AcquisitionCameraChannels {

  val sysName: String = "AC/HR"

  def build[F[_]](
    service: EpicsService[F],
    top:     NonEmptyString
  ): Resource[F, AcquisitionCameraChannels[F]] = for {
    tt   <- service.getChannel[String](top, "health.VAL").map(TelltaleChannel(sysName, _))
    flrd <- service.getChannel[String](top, "clfilterName.VAL")
    lns  <- service.getChannel[String](top, "lensSel.A")
    ndf  <- service.getChannel[String](top, "ndfilterSel.A")
    flt  <- service.getChannel[String](top, "clfilterSel.A")
    fcnt <- service.getChannel[String](top, "dc:detExposure.A")
    exp  <- service.getChannel[String](top, "dc:detExposure.B")
    out  <- service.getChannel[String](top, "dc:setObserve.A")
    drt  <- service.getChannel[String](top, "dc:setObserve.B")
    fln  <- service.getChannel[String](top, "dc:setObserve.C")
    sim  <- service.getChannel[String](top, "dc:setObserve.D")
    str  <- service.getChannel[String](top, "dc:setDhsInfo.A")
    opt  <- service.getChannel[String](top, "dc:setDhsInfo.B")
    typ  <- service.getChannel[String](top, "dc:detObstype.A")
    bin  <- service.getChannel[String](top, "dc:detFrameSize.A")
    wnd  <- service.getChannel[String](top, "dc:detFrameSize.B")
    ctx  <- service.getChannel[String](top, "dc:detFrameSize.C")
    cty  <- service.getChannel[String](top, "dc:detFrameSize.D")
    wid  <- service.getChannel[String](top, "dc:detFrameSize.E")
    hei  <- service.getChannel[String](top, "dc:detFrameSize.F")
    lab  <- service.getChannel[String](top, "observe.A")
    std  <- service.getChannel[CadDirective](top, "stop.DIR")
    obc  <- service.getChannel[CarState](top, "observeC.VAL")
  } yield AcquisitionCameraChannels(
    tt,
    flrd,
    lns,
    ndf,
    flt,
    fcnt,
    exp,
    out,
    drt,
    fln,
    sim,
    str,
    opt,
    typ,
    bin,
    wnd,
    ctx,
    cty,
    wid,
    hei,
    lab,
    std,
    obc
  )
}
