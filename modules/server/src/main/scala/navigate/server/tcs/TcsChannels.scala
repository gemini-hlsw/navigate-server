// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import navigate.epics.Channel
import navigate.epics.EpicsService
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.given
import navigate.server.acm.CadDirective
import navigate.server.epicsdata.BinaryOnOff
import navigate.server.epicsdata.BinaryOnOffCapitalized
import navigate.server.epicsdata.BinaryYesNo
import navigate.server.epicsdata.DirSuffix

import TcsChannels.*

case class TcsChannels[F[_]](
  /**
   * List of all TcsChannels. Channel -> Defines a raw channel Other cases -> Group of channels
   */
  telltale:             TelltaleChannel[F],
  telescopeParkDir:     Channel[F, CadDirective],
  mountFollow:          Channel[F, String],
  rotStopBrake:         Channel[F, String],
  rotParkDir:           Channel[F, CadDirective],
  rotFollow:            Channel[F, String],
  rotMoveAngle:         Channel[F, String],
  enclosure:            EnclosureChannels[F],
  sourceA:              TargetChannels[F],
  oiwfsTarget:          TargetChannels[F],
  wavelSourceA:         Channel[F, String],
  slew:                 SlewChannels[F],
  rotator:              RotatorChannels[F],
  origin:               OriginChannels[F],
  focusOffset:          Channel[F, String],
  p1ProbeTracking:      ProbeTrackingChannels[F],
  p1Probe:              ProbeChannels[F],
  p2ProbeTracking:      ProbeTrackingChannels[F],
  p2Probe:              ProbeChannels[F],
  oiProbeTracking:      ProbeTrackingChannels[F],
  oiProbe:              ProbeChannels[F],
  m1Guide:              Channel[F, String],
  m1GuideConfig:        M1GuideConfigChannels[F],
  m2Guide:              Channel[F, String],
  m2GuideMode:          Channel[F, String],
  m2GuideConfig:        M2GuideConfigChannels[F],
  m2GuideReset:         Channel[F, CadDirective],
  m2Follow:             Channel[F, String],
  mountGuide:           MountGuideChannels[F],
  oiwfs:                WfsChannels[F],
  guide:                GuideConfigStatusChannels[F],
  probeGuideMode:       ProbeGuideModeChannels[F],
  oiwfsSelect:          OiwfsSelectChannels[F],
  m2Baffles:            M2BafflesChannels[F],
  hrwfsMech:            AgMechChannels[F],
  scienceFoldMech:      AgMechChannels[F],
  aoFoldMech:           AgMechChannels[F],
  m1Channels:           M1Channels[F],
  nodState:             Channel[F, String],
  p1ProbeTrackingState: ProbeTrackingStateChannels[F],
  p2ProbeTrackingState: ProbeTrackingStateChannels[F],
  oiProbeTrackingState: ProbeTrackingStateChannels[F],
  targetAdjust:         AdjustChannels[F],
  originAdjust:         AdjustChannels[F],
  pointingAdjust:       PointingModelAdjustChannels[F],
  inPosition:           Channel[F, String],
  targetFilter:         TargetFilterChannels[F]
)

object TcsChannels {

  private val sysName: String = "TCS"

  // Next case classes are the channel groups
  case class M1GuideConfigChannels[F[_]](
    weighting: Channel[F, String],
    source:    Channel[F, String],
    frames:    Channel[F, String],
    filename:  Channel[F, String]
  )

  object M1GuideConfigChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop
    ): Resource[F, M1GuideConfigChannels[F]] = for {
      w <- service.getChannel[String](top.value, "m1GuideConfig.A")
      s <- service.getChannel[String](top.value, "m1GuideConfig.B")
      f <- service.getChannel[String](top.value, "m1GuideConfig.C")
      n <- service.getChannel[String](top.value, "m1GuideConfig.D")
    } yield M1GuideConfigChannels(w, s, f, n)
  }

  case class M2GuideConfigChannels[F[_]](
    source:     Channel[F, String],
    samplefreq: Channel[F, String],
    filter:     Channel[F, String],
    freq1:      Channel[F, String],
    freq2:      Channel[F, String],
    beam:       Channel[F, String],
    reset:      Channel[F, String]
  )

  object M2GuideConfigChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop
    ): Resource[F, M2GuideConfigChannels[F]] = for {
      sr <- service.getChannel[String](top.value, "m2GuideConfig.A")
      sf <- service.getChannel[String](top.value, "m2GuideConfig.B")
      fl <- service.getChannel[String](top.value, "m2GuideConfig.C")
      f1 <- service.getChannel[String](top.value, "m2GuideConfig.D")
      f2 <- service.getChannel[String](top.value, "m2GuideConfig.E")
      bm <- service.getChannel[String](top.value, "m2GuideConfig.F")
      rs <- service.getChannel[String](top.value, "m2GuideConfig.G")
    } yield M2GuideConfigChannels(sr, sf, fl, f1, f2, bm, rs)
  }

  case class MountGuideChannels[F[_]](
    mode:     Channel[F, String],
    source:   Channel[F, String],
    p1weight: Channel[F, String],
    p2weight: Channel[F, String]
  )

  object MountGuideChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop
    ): Resource[F, MountGuideChannels[F]] = for {
      mn <- service.getChannel[String](top.value, "mountGuideMode.A")
      sr <- service.getChannel[String](top.value, "mountGuideMode.B")
      p1 <- service.getChannel[String](top.value, "mountGuideMode.C")
      p2 <- service.getChannel[String](top.value, "mountGuideMode.D")
    } yield MountGuideChannels(mn, sr, p1, p2)
  }

  case class OiwfsSelectChannels[F[_]](
    oiName: Channel[F, String],
    output: Channel[F, String]
  )

  object OiwfsSelectChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop
    ): Resource[F, OiwfsSelectChannels[F]] = for {
      oi  <- service.getChannel[String](top.value, "oiwfsSelect.A")
      out <- service.getChannel[String](top.value, "oiwfsSelect.B")
    } yield OiwfsSelectChannels(oi, out)
  }

  case class EnclosureChannels[F[_]](
    ecsDomeMode:      Channel[F, String],
    ecsShutterMode:   Channel[F, String],
    ecsSlitHeight:    Channel[F, String],
    ecsDomeEnable:    Channel[F, String],
    ecsShutterEnable: Channel[F, String],
    ecsMoveAngle:     Channel[F, String],
    ecsShutterTop:    Channel[F, String],
    ecsShutterBottom: Channel[F, String],
    ecsVentGateEast:  Channel[F, String],
    ecsVentGateWest:  Channel[F, String]
  )

  case class M1Channels[F[_]](
    telltale:      TelltaleChannel[F],
    park:          Channel[F, String],
    figUpdates:    Channel[F, String],
    zero:          Channel[F, String],
    loadModelFile: Channel[F, String],
    saveModelFile: Channel[F, String],
    aoEnable:      Channel[F, BinaryOnOffCapitalized]
  )

  object M1Channels {
    def build[F[_]](
      service: EpicsService[F],
      tcsTop:  TcsTop,
      m1Top:   M1Top
    ): Resource[F, M1Channels[F]] = for {
      tt  <- service.getChannel[String](m1Top.value, "health.VAL").map(TelltaleChannel("M1", _))
      prk <- service.getChannel[String](tcsTop.value, "m1Park.A")
      fup <- service.getChannel[String](tcsTop.value, "m1FigUpdates.A")
      zr  <- service.getChannel[String](tcsTop.value, "m1ZeroSet.A")
      ld  <- service.getChannel[String](tcsTop.value, "m1ModelRestore.A")
      sv  <- service.getChannel[String](tcsTop.value, "m1ModelSave.A")
      ao  <- service.getChannel[BinaryOnOffCapitalized](m1Top.value, "aosOn.VAL")
    } yield M1Channels(
      tt,
      prk,
      fup,
      zr,
      ld,
      sv,
      ao
    )
  }

  case class TargetChannels[F[_]](
    objectName:     Channel[F, String],
    coordSystem:    Channel[F, String],
    coord1:         Channel[F, String],
    coord2:         Channel[F, String],
    epoch:          Channel[F, String],
    equinox:        Channel[F, String],
    parallax:       Channel[F, String],
    properMotion1:  Channel[F, String],
    properMotion2:  Channel[F, String],
    radialVelocity: Channel[F, String],
    brightness:     Channel[F, String],
    ephemerisFile:  Channel[F, String]
  )

  case class ProbeTrackingChannels[F[_]](
    nodachopa: Channel[F, String],
    nodachopb: Channel[F, String],
    nodbchopa: Channel[F, String],
    nodbchopb: Channel[F, String]
  )

  case class ProbeTrackingStateChannels[F[_]](
    nodachopa: Channel[F, String],
    nodachopb: Channel[F, String],
    nodbchopa: Channel[F, String],
    nodbchopb: Channel[F, String]
  )

  case class SlewChannels[F[_]](
    zeroChopThrow:            Channel[F, String],
    zeroSourceOffset:         Channel[F, String],
    zeroSourceDiffTrack:      Channel[F, String],
    zeroMountOffset:          Channel[F, String],
    zeroMountDiffTrack:       Channel[F, String],
    shortcircuitTargetFilter: Channel[F, String],
    shortcircuitMountFilter:  Channel[F, String],
    resetPointing:            Channel[F, String],
    stopGuide:                Channel[F, String],
    zeroGuideOffset:          Channel[F, String],
    zeroInstrumentOffset:     Channel[F, String],
    autoparkPwfs1:            Channel[F, String],
    autoparkPwfs2:            Channel[F, String],
    autoparkOiwfs:            Channel[F, String],
    autoparkGems:             Channel[F, String],
    autoparkAowfs:            Channel[F, String]
  )

  case class RotatorChannels[F[_]](
    ipa:     Channel[F, String],
    system:  Channel[F, String],
    equinox: Channel[F, String],
    iaa:     Channel[F, String]
  )

  case class OriginChannels[F[_]](
    xa: Channel[F, String],
    ya: Channel[F, String],
    xb: Channel[F, String],
    yb: Channel[F, String],
    xc: Channel[F, String],
    yc: Channel[F, String]
  )

  case class ProbeChannels[F[_]](
    parkDir: Channel[F, CadDirective],
    follow:  Channel[F, String]
  )

  case class AgMechChannels[F[_]](
    parkDir:  Channel[F, CadDirective],
    position: Channel[F, String]
  )

  object AgMechChannels {
    def build[F[_]](
      service: EpicsService[F],
      prefix:  String
    ): Resource[F, AgMechChannels[F]] =
      for {
        prk <- service.getChannel[CadDirective](s"${prefix}Park$DirSuffix")
        pos <- service.getChannel[String](s"${prefix}.A")
      } yield AgMechChannels(prk, pos)
  }

  // Build functions to construct each epics channel for each
  // channels group
  def buildEnclosureChannels[F[_]](
    service: EpicsService[F],
    top:     TcsTop
  ): Resource[F, EnclosureChannels[F]] =
    for {
      edm <- service.getChannel[String](top.value, "carouselMode.A")
      esm <- service.getChannel[String](top.value, "carouselMode.B")
      esh <- service.getChannel[String](top.value, "carouselMode.C")
      ede <- service.getChannel[String](top.value, "carouselMode.D")
      ese <- service.getChannel[String](top.value, "carouselMode.E")
      ema <- service.getChannel[String](top.value, "carousel.A")
      est <- service.getChannel[String](top.value, "shutter.A")
      esb <- service.getChannel[String](top.value, "shutter.B")
      eve <- service.getChannel[String](top.value, "ventgates.A")
      evw <- service.getChannel[String](top.value, "ventgates.B")
    } yield EnclosureChannels(
      edm,
      esm,
      esh,
      ede,
      ese,
      ema,
      est,
      esb,
      eve,
      evw
    )

  def buildTargetChannels[F[_]](
    service: EpicsService[F],
    prefix:  String
  ): Resource[F, TargetChannels[F]] =
    for {
      on  <- service.getChannel[String](prefix + ".A")
      cs  <- service.getChannel[String](prefix + ".B")
      co1 <- service.getChannel[String](prefix + ".C")
      co2 <- service.getChannel[String](prefix + ".D")
      eq  <- service.getChannel[String](prefix + ".E")
      ep  <- service.getChannel[String](prefix + ".F")
      pr  <- service.getChannel[String](prefix + ".G")
      pm1 <- service.getChannel[String](prefix + ".H")
      pm2 <- service.getChannel[String](prefix + ".I")
      rv  <- service.getChannel[String](prefix + ".J")
      br  <- service.getChannel[String](prefix + ".K")
      eph <- service.getChannel[String](prefix + ".L")
    } yield TargetChannels(
      on,
      cs,
      co1,
      co2,
      ep,
      eq,
      pr,
      pm1,
      pm2,
      rv,
      br,
      eph
    )

  def buildProbeTrackingChannels[F[_]](
    service: EpicsService[F],
    top:     TcsTop,
    name:    String
  ): Resource[F, ProbeTrackingChannels[F]] = for {
    aa <- service.getChannel[String](top.value, s"config$name.A")
    ab <- service.getChannel[String](top.value, s"config$name.B")
    ba <- service.getChannel[String](top.value, s"config$name.D")
    bb <- service.getChannel[String](top.value, s"config$name.E")
  } yield ProbeTrackingChannels(aa, ab, ba, bb)

  def buildProbeTrackingStateChannels[F[_]](
    service: EpicsService[F],
    top:     TcsTop,
    name:    String
  ): Resource[F, ProbeTrackingStateChannels[F]] = for {
    aa <- service.getChannel[String](top.value, s"config$name.VALA")
    ab <- service.getChannel[String](top.value, s"config$name.VALB")
    ba <- service.getChannel[String](top.value, s"config$name.VALD")
    bb <- service.getChannel[String](top.value, s"config$name.VALE")
  } yield ProbeTrackingStateChannels(aa, ab, ba, bb)

  def buildSlewChannels[F[_]](
    service: EpicsService[F],
    top:     TcsTop
  ): Resource[F, SlewChannels[F]] = for {
    zct <- service.getChannel[String](top.value, "slew.A")
    zso <- service.getChannel[String](top.value, "slew.B")
    zsd <- service.getChannel[String](top.value, "slew.C")
    zmo <- service.getChannel[String](top.value, "slew.D")
    zmd <- service.getChannel[String](top.value, "slew.E")
    fl1 <- service.getChannel[String](top.value, "slew.F")
    fl2 <- service.getChannel[String](top.value, "slew.G")
    rp  <- service.getChannel[String](top.value, "slew.H")
    sg  <- service.getChannel[String](top.value, "slew.I")
    zgo <- service.getChannel[String](top.value, "slew.J")
    zio <- service.getChannel[String](top.value, "slew.K")
    ap1 <- service.getChannel[String](top.value, "slew.L")
    ap2 <- service.getChannel[String](top.value, "slew.M")
    aoi <- service.getChannel[String](top.value, "slew.N")
    agm <- service.getChannel[String](top.value, "slew.O")
    aao <- service.getChannel[String](top.value, "slew.P")
  } yield SlewChannels(
    zct,
    zso,
    zsd,
    zmo,
    zmd,
    fl1,
    fl2,
    rp,
    sg,
    zgo,
    zio,
    ap1,
    ap2,
    aoi,
    agm,
    aao
  )

  def buildRotatorChannels[F[_]](
    service: EpicsService[F],
    top:     TcsTop
  ): Resource[F, RotatorChannels[F]] = for {
    ipa     <- service.getChannel[String](top.value, "rotator.A")
    system  <- service.getChannel[String](top.value, "rotator.B")
    equinox <- service.getChannel[String](top.value, "rotator.C")
    iaa     <- service.getChannel[String](top.value, "rotator.D")
  } yield RotatorChannels(
    ipa,
    system,
    equinox,
    iaa
  )

  def buildOriginChannels[F[_]](
    service: EpicsService[F],
    top:     TcsTop
  ): Resource[F, OriginChannels[F]] = for {
    xa <- service.getChannel[String](top.value, "poriginA.A")
    ya <- service.getChannel[String](top.value, "poriginA.B")
    xb <- service.getChannel[String](top.value, "poriginB.A")
    yb <- service.getChannel[String](top.value, "poriginB.B")
    xc <- service.getChannel[String](top.value, "poriginC.A")
    yc <- service.getChannel[String](top.value, "poriginC.B")
  } yield OriginChannels(
    xa,
    ya,
    xb,
    yb,
    xc,
    yc
  )

  def buildProbeChannels[F[_]](
    service: EpicsService[F],
    prefix:  String
  ): Resource[F, ProbeChannels[F]] = for {
    pd <- service.getChannel[CadDirective](s"${prefix}Park${DirSuffix}")
    fl <- service.getChannel[String](s"${prefix}Follow.A")
  } yield ProbeChannels(pd, fl)

  case class WfsObserveChannels[F[_]](
    numberOfExposures: Channel[F, String],
    interval:          Channel[F, String],
    options:           Channel[F, String],
    label:             Channel[F, String],
    output:            Channel[F, String],
    path:              Channel[F, String],
    fileName:          Channel[F, String]
  )

  object WfsObserveChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop,
      wfs:     String
    ): Resource[F, WfsObserveChannels[F]] = for {
      ne <- service.getChannel[String](top.value, s"${wfs}Observe.A")
      in <- service.getChannel[String](top.value, s"${wfs}Observe.B")
      op <- service.getChannel[String](top.value, s"${wfs}Observe.C")
      lb <- service.getChannel[String](top.value, s"${wfs}Observe.D")
      ou <- service.getChannel[String](top.value, s"${wfs}Observe.E")
      pa <- service.getChannel[String](top.value, s"${wfs}Observe.F")
      fn <- service.getChannel[String](top.value, s"${wfs}Observe.G")
    } yield WfsObserveChannels(
      ne,
      in,
      op,
      lb,
      ou,
      pa,
      fn
    )
  }

  case class WfsClosedLoopChannels[F[_]](
    global:      Channel[F, String],
    average:     Channel[F, String],
    zernikes2m2: Channel[F, String],
    mult:        Channel[F, String]
  )

  object WfsClosedLoopChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop,
      wfs:     String
    ): Resource[F, WfsClosedLoopChannels[F]] = for {
      gl <- service.getChannel[String](top.value, s"${wfs}Seq.A")
      av <- service.getChannel[String](top.value, s"${wfs}Seq.B")
      z2 <- service.getChannel[String](top.value, s"${wfs}Seq.C")
      m  <- service.getChannel[String](top.value, s"${wfs}Seq.D")
    } yield WfsClosedLoopChannels(gl, av, z2, m)
  }

  case class WfsChannels[F[_]](
    observe:    WfsObserveChannels[F],
    stop:       Channel[F, CadDirective],
    procParams: Channel[F, String],
    dark:       Channel[F, String],
    closedLoop: WfsClosedLoopChannels[F]
  )

  object WfsChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop,
      wfsl:    String,
      wfss:    String
    ): Resource[F, WfsChannels[F]] = for {
      ob <- WfsObserveChannels.build(service, top, wfsl)
      st <- service.getChannel[CadDirective](top.value, s"${wfsl}StopObserve${DirSuffix}")
      pr <- service.getChannel[String](top.value, s"${wfss}detSigInit.A")
      dk <- service.getChannel[String](top.value, s"${wfss}SeqDark.A")
      cl <- WfsClosedLoopChannels.build(service, top, wfss)
    } yield WfsChannels(ob, st, pr, dk, cl)
  }

  case class GuideConfigStatusChannels[F[_]](
    pwfs1Integrating: Channel[F, BinaryYesNo],
    pwfs2Integrating: Channel[F, BinaryYesNo],
    oiwfsIntegrating: Channel[F, BinaryYesNo],
    m2State:          Channel[F, BinaryOnOff],
    absorbTipTilt:    Channel[F, Int],
    m2ComaCorrection: Channel[F, BinaryOnOff],
    m1State:          Channel[F, BinaryOnOff],
    m1Source:         Channel[F, String],
    p1ProbeGuide:     Channel[F, Double],
    p2ProbeGuide:     Channel[F, Double],
    oiProbeGuide:     Channel[F, Double],
    p1ProbeGuided:    Channel[F, Double],
    p2ProbeGuided:    Channel[F, Double],
    oiProbeGuided:    Channel[F, Double],
    mountP1Weight:    Channel[F, Double],
    mountP2Weight:    Channel[F, Double],
    m2P1Guide:        Channel[F, String],
    m2P2Guide:        Channel[F, String],
    m2OiGuide:        Channel[F, String],
    m2AoGuide:        Channel[F, String]
  )

  object GuideConfigStatusChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop
    ): Resource[F, GuideConfigStatusChannels[F]] = for {
      p1i   <- service.getChannel[BinaryYesNo](top.value, "drives:p1Integrating.VAL")
      p2i   <- service.getChannel[BinaryYesNo](top.value, "drives:p2Integrating.VAL")
      oii   <- service.getChannel[BinaryYesNo](top.value, "drives:oiIntegrating.VAL")
      m2    <- service.getChannel[BinaryOnOff](top.value, "om:m2GuideState.VAL")
      tt    <- service.getChannel[Int](top.value, "absorbTipTiltFlag.VAL")
      cm    <- service.getChannel[BinaryOnOff](top.value, "im:m2gm:comaCorrectFlag.VAL")
      m1    <- service.getChannel[BinaryOnOff](top.value, "im:m1GuideOn.VAL")
      m1s   <- service.getChannel[String](top.value, "m1GuideConfig.VALB")
      p1pg  <- service.getChannel[Double](top.value, "ak:wfsguide:p1weight.VAL")
      p2pg  <- service.getChannel[Double](top.value, "ak:wfsguide:p2weight.VAL")
      oipg  <- service.getChannel[Double](top.value, "ak:wfsguide:oiweight.VAL")
      p1pd  <- service.getChannel[Double](top.value, "wfsGuideMode:p1.VAL")
      p2pd  <- service.getChannel[Double](top.value, "wfsGuideMode:p2.VAL")
      oipd  <- service.getChannel[Double](top.value, "wfsGuideMode:oi.VAL")
      p1w   <- service.getChannel[Double](top.value, "mountGuideMode.VALC")
      p2w   <- service.getChannel[Double](top.value, "mountGuideMode.VALD")
      m2p1g <- service.getChannel[String](top.value, "drives:p1GuideConfig.VAL")
      m2p2g <- service.getChannel[String](top.value, "drives:p2GuideConfig.VAL")
      m2oig <- service.getChannel[String](top.value, "drives:oiGuideConfig.VAL")
      m2aog <- service.getChannel[String](top.value, "drives:aoGuideConfig.VAL")
    } yield GuideConfigStatusChannels(
      p1i,
      p2i,
      oii,
      m2,
      tt,
      cm,
      m1,
      m1s,
      p1pg,
      p2pg,
      oipg,
      p1pd,
      p2pd,
      oipd,
      p1w,
      p2w,
      m2p1g,
      m2p2g,
      m2oig,
      m2aog
    )
  }

  case class ProbeGuideModeChannels[F[_]](
    state: Channel[F, String],
    from:  Channel[F, String],
    to:    Channel[F, String]
  )

  object ProbeGuideModeChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop
    ): Resource[F, ProbeGuideModeChannels[F]] = for {
      state <- service.getChannel[String](top.value, "wfsGuideMode.A")
      from  <- service.getChannel[String](top.value, "wfsGuideMode.B")
      to    <- service.getChannel[String](top.value, "wfsGuideMode.C")
    } yield ProbeGuideModeChannels(state, from, to)
  }

  case class M2BafflesChannels[F[_]](
    deployBaffle:  Channel[F, String],
    centralBaffle: Channel[F, String]
  )

  object M2BafflesChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop
    ): Resource[F, M2BafflesChannels[F]] = for {
      dpl <- service.getChannel[String](top.value, "m2Baffle.A")
      cnt <- service.getChannel[String](top.value, "m2Baffle.B")
    } yield M2BafflesChannels(dpl, cnt)
  }

  case class AdjustChannels[F[_]](
    frame:  Channel[F, String],
    size:   Channel[F, String],
    angle:  Channel[F, String],
    vtMask: Channel[F, String]
  )

  object AdjustChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop,
      name:    String
    ): Resource[F, AdjustChannels[F]] = for {
      frm <- service.getChannel[String](top.value, s"${name}Adjust.A")
      sz  <- service.getChannel[String](top.value, s"${name}Adjust.B")
      an  <- service.getChannel[String](top.value, s"${name}Adjust.C")
      vt  <- service.getChannel[String](top.value, s"${name}Adjust.D")
    } yield AdjustChannels(frm, sz, an, vt)
  }

  case class PointingModelAdjustChannels[F[_]](
    frame: Channel[F, String],
    size:  Channel[F, String],
    angle: Channel[F, String]
  )

  object PointingModelAdjustChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop,
      cadName: String
    ): Resource[F, PointingModelAdjustChannels[F]] = for {
      frm <- service.getChannel[String](top.value, s"$cadName.A")
      sze <- service.getChannel[String](top.value, s"$cadName.A")
      ang <- service.getChannel[String](top.value, s"$cadName.A")
    } yield PointingModelAdjustChannels[F](
      frm,
      sze,
      ang
    )
  }

  case class TargetFilterChannels[F[_]](
    bandWidth:    Channel[F, String],
    maxVelocity:  Channel[F, String],
    grabRadius:   Channel[F, String],
    shortCircuit: Channel[F, String]
  )

  object TargetFilterChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     TcsTop
    ): Resource[F, TargetFilterChannels[F]] = for {
      bw <- service.getChannel[String](top.value, "filter1.A")
      mv <- service.getChannel[String](top.value, "filter1.B")
      gr <- service.getChannel[String](top.value, "filter1.C")
      sc <- service.getChannel[String](top.value, "filter1.D")
    } yield TargetFilterChannels(
      bw,
      mv,
      gr,
      sc
    )
  }

  /**
   * Build all TcsChannels It will construct the desired raw channel or call the build function for
   * channels group
   *
   * @param service
   *   Epics service to handle channels
   * @param top
   *   Prefix string of epics channel
   * @return
   */
  def buildChannels[F[_]](
    service: EpicsService[F],
    tcsTop:  TcsTop,
    m1Top:   M1Top
  ): Resource[F, TcsChannels[F]] = {
    def telltaleChannel(top: NonEmptyString, channel: String): Resource[F, TelltaleChannel[F]] =
      service.getChannel[String](top, channel).map(TelltaleChannel(sysName, _))

    for {
      tt   <- telltaleChannel(tcsTop.value, "sad:health.VAL")
      tpd  <- service.getChannel[CadDirective](tcsTop.value, s"telpark$DirSuffix")
      mf   <- service.getChannel[String](tcsTop.value, "mcFollow.A")
      rsb  <- service.getChannel[String](tcsTop.value, "rotStop.B")
      rpd  <- service.getChannel[CadDirective](tcsTop.value, s"rotPark$DirSuffix")
      rf   <- service.getChannel[String](tcsTop.value, "crFollow.A")
      rma  <- service.getChannel[String](tcsTop.value, "rotMove.A")
      ecs  <- buildEnclosureChannels(service, tcsTop)
      sra  <- buildTargetChannels(service, s"${tcsTop.value}sourceA")
      oit  <- buildTargetChannels(service, s"${tcsTop.value}oiwfs")
      wva  <- service.getChannel[String](tcsTop.value, "wavelSourceA.A")
      slw  <- buildSlewChannels(service, tcsTop)
      rot  <- buildRotatorChannels(service, tcsTop)
      org  <- buildOriginChannels(service, tcsTop)
      foc  <- service.getChannel[String](tcsTop.value, "dtelFocus.A")
      p1g  <- buildProbeTrackingChannels(service, tcsTop, "Pwfs1")
      p1p  <- buildProbeChannels(service, s"${tcsTop.value.value}pwfs1")
      p2g  <- buildProbeTrackingChannels(service, tcsTop, "Pwfs2")
      p2p  <- buildProbeChannels(service, s"${tcsTop.value.value}pwfs2")
      oig  <- buildProbeTrackingChannels(service, tcsTop, "Oiwfs")
      op   <- buildProbeChannels(service, s"${tcsTop.value.value}oiwfs")
      m1g  <- service.getChannel[String](tcsTop.value, "m1GuideMode.A")
      m1gc <- M1GuideConfigChannels.build(service, tcsTop)
      m2g  <- service.getChannel[String](tcsTop.value, "m2GuideControl.A")
      m2gm <- service.getChannel[String](tcsTop.value, "m2GuideMode.A")
      m2gc <- M2GuideConfigChannels.build(service, tcsTop)
      m2gr <- service.getChannel[CadDirective](tcsTop.value, s"m2GuideReset$DirSuffix")
      m2f  <- service.getChannel[String](tcsTop.value, "m2Follow.A")
      mng  <- MountGuideChannels.build(service, tcsTop)
      oi   <- WfsChannels.build(service, tcsTop, "oiwfs", "oi")
      gd   <- GuideConfigStatusChannels.build(service, tcsTop)
      gm   <- ProbeGuideModeChannels.build(service, tcsTop)
      os   <- OiwfsSelectChannels.build(service, tcsTop)
      bf   <- M2BafflesChannels.build(service, tcsTop)
      hrm  <- AgMechChannels.build(service, s"${tcsTop.value}hrwfs")
      sfm  <- AgMechChannels.build(service, s"${tcsTop.value}scienceFold")
      aom  <- AgMechChannels.build(service, s"${tcsTop.value}aoFold")
      m1   <- M1Channels.build(service, tcsTop, m1Top)
      trad <- AdjustChannels.build(service, tcsTop, "target")
      orad <- AdjustChannels.build(service, tcsTop, "po")
      pmad <- PointingModelAdjustChannels.build(service, tcsTop, "collAdjust")
      nodS <- service.getChannel[String](tcsTop.value, "sad:nodState.VAL")
      p1gs <- buildProbeTrackingStateChannels(service, tcsTop, "Pwfs1")
      p2gs <- buildProbeTrackingStateChannels(service, tcsTop, "Pwfs2")
      oigs <- buildProbeTrackingStateChannels(service, tcsTop, "Oiwfs")
      inpo <- service.getChannel[String](tcsTop.value, "sad:inPosition.VAL")
      tf   <- TargetFilterChannels.build(service, tcsTop)
    } yield TcsChannels[F](
      tt,
      tpd,
      mf,
      rsb,
      rpd,
      rf,
      rma,
      ecs,
      sra,
      oit,
      wva,
      slw,
      rot,
      org,
      foc,
      p1g,
      p1p,
      p2g,
      p2p,
      oig,
      op,
      m1g,
      m1gc,
      m2g,
      m2gm,
      m2gc,
      m2gr,
      m2f,
      mng,
      oi,
      gd,
      gm,
      os,
      bf,
      hrm,
      sfm,
      aom,
      m1,
      nodS,
      p1gs,
      p2gs,
      oigs,
      trad,
      orad,
      pmad,
      inpo,
      tf
    )
  }
}
