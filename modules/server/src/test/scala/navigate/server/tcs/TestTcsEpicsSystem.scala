// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.Monad
import cats.Parallel
import cats.effect.Ref
import monocle.Focus
import monocle.Lens
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.TestChannel
import navigate.epics.VerifiedEpics
import navigate.epics.VerifiedEpics.VerifiedEpics
import navigate.server.ApplyCommandResult
import navigate.server.acm.CadDirective
import navigate.server.acm.GeminiApplyCommand
import navigate.server.epicsdata.BinaryOnOff
import navigate.server.epicsdata.BinaryYesNo
import navigate.server.tcs.TcsChannels.{
  EnclosureChannels,
  GuideConfigStatusChannels,
  GuiderGainsChannels,
  M1GuideConfigChannels,
  M2GuideConfigChannels,
  MountGuideChannels,
  OiwfsSelectChannels,
  OriginChannels,
  ProbeChannels,
  ProbeGuideModeChannels,
  ProbeTrackingChannels,
  RotatorChannels,
  SlewChannels,
  TargetChannels,
  WfsChannels,
  WfsClosedLoopChannels,
  WfsObserveChannels
}

import scala.concurrent.duration.FiniteDuration

object TestTcsEpicsSystem {

  class TestApplyCommand[F[_]: Applicative] extends GeminiApplyCommand[F] {
    override def post(timeout: FiniteDuration): VerifiedEpics[F, F, ApplyCommandResult] =
      VerifiedEpics.pureF[F, F, ApplyCommandResult](ApplyCommandResult.Completed)
  }

  case class EnclosureChannelsState(
    ecsDomeMode:      TestChannel.State[String],
    ecsShutterMode:   TestChannel.State[String],
    ecsSlitHeight:    TestChannel.State[String],
    ecsDomeEnable:    TestChannel.State[String],
    ecsShutterEnable: TestChannel.State[String],
    ecsMoveAngle:     TestChannel.State[String],
    ecsShutterTop:    TestChannel.State[String],
    ecsShutterBottom: TestChannel.State[String],
    ecsVentGateEast:  TestChannel.State[String],
    ecsVentGateWest:  TestChannel.State[String]
  )

  case class TargetChannelsState(
    objectName:     TestChannel.State[String],
    coordSystem:    TestChannel.State[String],
    coord1:         TestChannel.State[String],
    coord2:         TestChannel.State[String],
    epoch:          TestChannel.State[String],
    equinox:        TestChannel.State[String],
    parallax:       TestChannel.State[String],
    properMotion1:  TestChannel.State[String],
    properMotion2:  TestChannel.State[String],
    radialVelocity: TestChannel.State[String],
    brightness:     TestChannel.State[String],
    ephemerisFile:  TestChannel.State[String]
  )

  case class ProbeTrackingState(
    nodAchopA: TestChannel.State[String],
    nodAchopB: TestChannel.State[String],
    nodBchopA: TestChannel.State[String],
    nodBchopB: TestChannel.State[String]
  )

  case class ProbeState(
    parkDir: TestChannel.State[CadDirective],
    follow:  TestChannel.State[String]
  )

  case class SlewChannelsState(
    zeroChopThrow:            TestChannel.State[String],
    zeroSourceOffset:         TestChannel.State[String],
    zeroSourceDiffTrack:      TestChannel.State[String],
    zeroMountOffset:          TestChannel.State[String],
    zeroMountDiffTrack:       TestChannel.State[String],
    shortcircuitTargetFilter: TestChannel.State[String],
    shortcircuitMountFilter:  TestChannel.State[String],
    resetPointing:            TestChannel.State[String],
    stopGuide:                TestChannel.State[String],
    zeroGuideOffset:          TestChannel.State[String],
    zeroInstrumentOffset:     TestChannel.State[String],
    autoparkPwfs1:            TestChannel.State[String],
    autoparkPwfs2:            TestChannel.State[String],
    autoparkOiwfs:            TestChannel.State[String],
    autoparkGems:             TestChannel.State[String],
    autoparkAowfs:            TestChannel.State[String]
  )

  case class RotatorChannelState(
    ipa:     TestChannel.State[String],
    system:  TestChannel.State[String],
    equinox: TestChannel.State[String],
    iaa:     TestChannel.State[String]
  )

  case class OriginChannelState(
    xa: TestChannel.State[String],
    ya: TestChannel.State[String],
    xb: TestChannel.State[String],
    yb: TestChannel.State[String],
    xc: TestChannel.State[String],
    yc: TestChannel.State[String]
  )

  case class WfsObserveChannelState(
    numberOfExposures: TestChannel.State[String],
    interval:          TestChannel.State[String],
    options:           TestChannel.State[String],
    label:             TestChannel.State[String],
    output:            TestChannel.State[String],
    path:              TestChannel.State[String],
    fileName:          TestChannel.State[String]
  )

  object WfsObserveChannelState {
    val default: WfsObserveChannelState = WfsObserveChannelState(
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default
    )
  }

  case class WfsClosedLoopChannelState(
    global:      TestChannel.State[String],
    average:     TestChannel.State[String],
    zernikes2m2: TestChannel.State[String],
    mult:        TestChannel.State[String]
  )

  object WfsClosedLoopChannelState {
    val default: WfsClosedLoopChannelState = WfsClosedLoopChannelState(
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default
    )
  }

  case class ProbeGuideModeState(
    state: TestChannel.State[String],
    from:  TestChannel.State[String],
    to:    TestChannel.State[String]
  )

  case class OiwfsSelectState(
    oiwfsName: TestChannel.State[String],
    output:    TestChannel.State[String]
  )

  object OiwfsSelectState {
    val default: OiwfsSelectState = OiwfsSelectState(
      TestChannel.State.default,
      TestChannel.State.default
    )
  }

  object ProbeGuideModeState {
    val default: ProbeGuideModeState = ProbeGuideModeState(
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default
    )
  }

  case class WfsChannelState(
    observe:    WfsObserveChannelState,
    stop:       TestChannel.State[CadDirective],
    signalProc: TestChannel.State[String],
    dark:       TestChannel.State[String],
    closedLoop: WfsClosedLoopChannelState
  )

  object WfsChannelState {
    val default: WfsChannelState = WfsChannelState(
      WfsObserveChannelState.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      WfsClosedLoopChannelState.default
    )
  }

  case class GuiderGainsState(
    p1TipGain:   TestChannel.State[String],
    p1TiltGain:  TestChannel.State[String],
    p1FocusGain: TestChannel.State[String],
    p1Reset:     TestChannel.State[BinaryYesNo],
    p2TipGain:   TestChannel.State[String],
    p2TiltGain:  TestChannel.State[String],
    p2FocusGain: TestChannel.State[String],
    p2Reset:     TestChannel.State[BinaryYesNo],
    oiTipGain:   TestChannel.State[String],
    oiTiltGain:  TestChannel.State[String],
    oiFocusGain: TestChannel.State[String],
    oiReset:     TestChannel.State[BinaryYesNo]
  )

  object GuiderGainsState {
    val default: GuiderGainsState = GuiderGainsState(
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default
    )
  }

  case class State(
    telltale:         TestChannel.State[String],
    telescopeParkDir: TestChannel.State[CadDirective],
    mountFollow:      TestChannel.State[String],
    rotStopBrake:     TestChannel.State[String],
    rotParkDir:       TestChannel.State[CadDirective],
    rotFollow:        TestChannel.State[String],
    rotMoveAngle:     TestChannel.State[String],
    enclosure:        EnclosureChannelsState,
    sourceA:          TargetChannelsState,
    oiwfsTarget:      TargetChannelsState,
    wavelSourceA:     TestChannel.State[String],
    slew:             SlewChannelsState,
    rotator:          RotatorChannelState,
    origin:           OriginChannelState,
    focusOffset:      TestChannel.State[String],
    oiwfsTracking:    ProbeTrackingState,
    oiwfsProbe:       ProbeState,
    oiWfs:            WfsChannelState,
    m1Guide:          TestChannel.State[String],
    m1GuideConfig:    M1GuideConfigState,
    m2Guide:          TestChannel.State[String],
    m2GuideMode:      TestChannel.State[String],
    m2GuideConfig:    M2GuideConfigState,
    m2GuideReset:     TestChannel.State[CadDirective],
    mountGuide:       MountGuideState,
    guideStatus:      GuideConfigState,
    guiderGains:      GuiderGainsState,
    probeGuideMode:   ProbeGuideModeState,
    oiwfsSelect:      OiwfsSelectState
  )

  val defaultState: State = State(
    telltale = TestChannel.State.default,
    telescopeParkDir = TestChannel.State.default,
    mountFollow = TestChannel.State.default,
    rotStopBrake = TestChannel.State.default,
    rotParkDir = TestChannel.State.default,
    rotFollow = TestChannel.State.default,
    rotMoveAngle = TestChannel.State.default,
    enclosure = EnclosureChannelsState(
      ecsDomeMode = TestChannel.State.default,
      ecsShutterMode = TestChannel.State.default,
      ecsSlitHeight = TestChannel.State.default,
      ecsDomeEnable = TestChannel.State.default,
      ecsShutterEnable = TestChannel.State.default,
      ecsMoveAngle = TestChannel.State.default,
      ecsShutterTop = TestChannel.State.default,
      ecsShutterBottom = TestChannel.State.default,
      ecsVentGateEast = TestChannel.State.default,
      ecsVentGateWest = TestChannel.State.default
    ),
    sourceA = TargetChannelsState(
      objectName = TestChannel.State.default,
      coordSystem = TestChannel.State.default,
      coord1 = TestChannel.State.default,
      coord2 = TestChannel.State.default,
      epoch = TestChannel.State.default,
      equinox = TestChannel.State.default,
      parallax = TestChannel.State.default,
      properMotion1 = TestChannel.State.default,
      properMotion2 = TestChannel.State.default,
      radialVelocity = TestChannel.State.default,
      brightness = TestChannel.State.default,
      ephemerisFile = TestChannel.State.default
    ),
    oiwfsTarget = TargetChannelsState(
      objectName = TestChannel.State.default,
      coordSystem = TestChannel.State.default,
      coord1 = TestChannel.State.default,
      coord2 = TestChannel.State.default,
      epoch = TestChannel.State.default,
      equinox = TestChannel.State.default,
      parallax = TestChannel.State.default,
      properMotion1 = TestChannel.State.default,
      properMotion2 = TestChannel.State.default,
      radialVelocity = TestChannel.State.default,
      brightness = TestChannel.State.default,
      ephemerisFile = TestChannel.State.default
    ),
    wavelSourceA = TestChannel.State.default,
    slew = SlewChannelsState(
      zeroChopThrow = TestChannel.State.default,
      zeroSourceOffset = TestChannel.State.default,
      zeroSourceDiffTrack = TestChannel.State.default,
      zeroMountOffset = TestChannel.State.default,
      zeroMountDiffTrack = TestChannel.State.default,
      shortcircuitTargetFilter = TestChannel.State.default,
      shortcircuitMountFilter = TestChannel.State.default,
      resetPointing = TestChannel.State.default,
      stopGuide = TestChannel.State.default,
      zeroGuideOffset = TestChannel.State.default,
      zeroInstrumentOffset = TestChannel.State.default,
      autoparkPwfs1 = TestChannel.State.default,
      autoparkPwfs2 = TestChannel.State.default,
      autoparkOiwfs = TestChannel.State.default,
      autoparkGems = TestChannel.State.default,
      autoparkAowfs = TestChannel.State.default
    ),
    rotator = RotatorChannelState(
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default
    ),
    origin = OriginChannelState(
      xa = TestChannel.State.default,
      ya = TestChannel.State.default,
      xb = TestChannel.State.default,
      yb = TestChannel.State.default,
      xc = TestChannel.State.default,
      yc = TestChannel.State.default
    ),
    focusOffset = TestChannel.State.default,
    oiwfsTracking = ProbeTrackingState(
      nodAchopA = TestChannel.State.default,
      nodAchopB = TestChannel.State.default,
      nodBchopA = TestChannel.State.default,
      nodBchopB = TestChannel.State.default
    ),
    oiwfsProbe =
      ProbeState(parkDir = TestChannel.State.default, follow = TestChannel.State.default),
    oiWfs = WfsChannelState.default,
    m1Guide = TestChannel.State.default,
    m1GuideConfig = M1GuideConfigState.default,
    m2Guide = TestChannel.State.default,
    m2GuideMode = TestChannel.State.default,
    m2GuideConfig = M2GuideConfigState.default,
    m2GuideReset = TestChannel.State.default,
    mountGuide = MountGuideState.default,
    guideStatus = GuideConfigState.default,
    guiderGains = GuiderGainsState.default,
    probeGuideMode = ProbeGuideModeState.default,
    oiwfsSelect = OiwfsSelectState.default
  )

  def buildEnclosureChannels[F[_]: Applicative](s: Ref[F, State]): EnclosureChannels[F] =
    EnclosureChannels[F](
      ecsDomeMode = new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsDomeMode)),
      ecsShutterMode =
        new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsShutterMode)),
      ecsSlitHeight = new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsSlitHeight)),
      ecsDomeEnable = new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsDomeEnable)),
      ecsShutterEnable =
        new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsShutterEnable)),
      ecsMoveAngle = new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsMoveAngle)),
      ecsShutterTop = new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsShutterTop)),
      ecsShutterBottom =
        new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsShutterBottom)),
      ecsVentGateEast =
        new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsVentGateEast)),
      ecsVentGateWest =
        new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsVentGateWest))
    )

  def buildTargetChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, TargetChannelsState]
  ): TargetChannels[F] =
    TargetChannels[F](
      objectName =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.objectName))),
      coordSystem =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.coordSystem))),
      coord1 =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.coord1))),
      coord2 =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.coord2))),
      epoch = new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.epoch))),
      equinox =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.equinox))),
      parallax =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.parallax))),
      properMotion1 =
        new TestChannel[F, State, String](s,
                                          l.andThen(Focus[TargetChannelsState](_.properMotion1))
        ),
      properMotion2 =
        new TestChannel[F, State, String](s,
                                          l.andThen(Focus[TargetChannelsState](_.properMotion2))
        ),
      radialVelocity =
        new TestChannel[F, State, String](s,
                                          l.andThen(Focus[TargetChannelsState](_.radialVelocity))
        ),
      brightness =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.brightness))),
      ephemerisFile =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.ephemerisFile)))
    )

  def buildSlewChannels[F[_]: Applicative](
    s: Ref[F, State]
  ): SlewChannels[F] =
    SlewChannels(
      zeroChopThrow = new TestChannel[F, State, String](s, Focus[State](_.slew.zeroChopThrow)),
      zeroSourceOffset =
        new TestChannel[F, State, String](s, Focus[State](_.slew.zeroSourceOffset)),
      zeroSourceDiffTrack = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.zeroSourceDiffTrack)
      ),
      zeroMountOffset = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.zeroMountOffset)
      ),
      zeroMountDiffTrack = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.zeroMountDiffTrack)
      ),
      shortcircuitTargetFilter = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.shortcircuitTargetFilter)
      ),
      shortcircuitMountFilter = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.shortcircuitMountFilter)
      ),
      resetPointing = new TestChannel[F, State, String](s, Focus[State](_.slew.resetPointing)),
      stopGuide = new TestChannel[F, State, String](s, Focus[State](_.slew.stopGuide)),
      zeroGuideOffset = new TestChannel[F, State, String](s, Focus[State](_.slew.zeroGuideOffset)),
      zeroInstrumentOffset = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.zeroInstrumentOffset)
      ),
      autoparkPwfs1 = new TestChannel[F, State, String](s, Focus[State](_.slew.autoparkPwfs1)),
      autoparkPwfs2 = new TestChannel[F, State, String](s, Focus[State](_.slew.autoparkPwfs2)),
      autoparkOiwfs = new TestChannel[F, State, String](s, Focus[State](_.slew.autoparkOiwfs)),
      autoparkGems = new TestChannel[F, State, String](s, Focus[State](_.slew.autoparkGems)),
      autoparkAowfs = new TestChannel[F, State, String](s, Focus[State](_.slew.autoparkAowfs))
    )

  def buildRotatorChannels[F[_]: Applicative](s: Ref[F, State]): RotatorChannels[F] =
    RotatorChannels(
      new TestChannel[F, State, String](s, Focus[State](_.rotator.ipa)),
      new TestChannel[F, State, String](s, Focus[State](_.rotator.system)),
      new TestChannel[F, State, String](s, Focus[State](_.rotator.equinox)),
      new TestChannel[F, State, String](s, Focus[State](_.rotator.iaa))
    )

  def buildOriginChannels[F[_]: Applicative](s: Ref[F, State]): OriginChannels[F] =
    OriginChannels(
      new TestChannel[F, State, String](s, Focus[State](_.origin.xa)),
      new TestChannel[F, State, String](s, Focus[State](_.origin.ya)),
      new TestChannel[F, State, String](s, Focus[State](_.origin.xb)),
      new TestChannel[F, State, String](s, Focus[State](_.origin.yb)),
      new TestChannel[F, State, String](s, Focus[State](_.origin.xc)),
      new TestChannel[F, State, String](s, Focus[State](_.origin.yc))
    )

  def buildProbeTrackingChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, ProbeTrackingState]
  ): ProbeTrackingChannels[F] = ProbeTrackingChannels(
    new TestChannel[F, State, String](s, l.andThen(Focus[ProbeTrackingState](_.nodAchopA))),
    new TestChannel[F, State, String](s, l.andThen(Focus[ProbeTrackingState](_.nodAchopB))),
    new TestChannel[F, State, String](s, l.andThen(Focus[ProbeTrackingState](_.nodBchopA))),
    new TestChannel[F, State, String](s, l.andThen(Focus[ProbeTrackingState](_.nodBchopB)))
  )

  def buildProbeChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, ProbeState]
  ): ProbeChannels[F] = ProbeChannels(
    new TestChannel[F, State, CadDirective](s, l.andThen(Focus[ProbeState](_.parkDir))),
    new TestChannel[F, State, String](s, l.andThen(Focus[ProbeState](_.follow)))
  )

  case class M1GuideConfigState(
    weighting: TestChannel.State[String],
    source:    TestChannel.State[String],
    frames:    TestChannel.State[String],
    filename:  TestChannel.State[String]
  )

  object M1GuideConfigState {
    val default: M1GuideConfigState = M1GuideConfigState(
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default
    )
  }

  def buildM1GuideConfigChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, M1GuideConfigState]
  ): M1GuideConfigChannels[F] = M1GuideConfigChannels(
    new TestChannel[F, State, String](s, l.andThen(Focus[M1GuideConfigState](_.weighting))),
    new TestChannel[F, State, String](s, l.andThen(Focus[M1GuideConfigState](_.source))),
    new TestChannel[F, State, String](s, l.andThen(Focus[M1GuideConfigState](_.frames))),
    new TestChannel[F, State, String](s, l.andThen(Focus[M1GuideConfigState](_.filename)))
  )

  case class GuideConfigState(
    pwfs1Integrating: TestChannel.State[BinaryYesNo],
    pwfs2Integrating: TestChannel.State[BinaryYesNo],
    oiwfsIntegrating: TestChannel.State[BinaryYesNo],
    m2State:          TestChannel.State[BinaryOnOff],
    absorbTipTilt:    TestChannel.State[Int],
    m2ComaCorrection: TestChannel.State[BinaryOnOff],
    m1State:          TestChannel.State[BinaryOnOff],
    m1Source:         TestChannel.State[String],
    p1ProbeGuide:     TestChannel.State[Double],
    p2ProbeGuide:     TestChannel.State[Double],
    oiProbeGuide:     TestChannel.State[Double],
    p1ProbeGuided:    TestChannel.State[Double],
    p2ProbeGuided:    TestChannel.State[Double],
    oiProbeGuided:    TestChannel.State[Double],
    mountP1Weight:    TestChannel.State[Double],
    mountP2Weight:    TestChannel.State[Double],
    m2P1Guide:        TestChannel.State[String],
    m2P2Guide:        TestChannel.State[String],
    m2OiGuide:        TestChannel.State[String],
    m2AoGuide:        TestChannel.State[String]
  )

  object GuideConfigState {
    val default: GuideConfigState = GuideConfigState(
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default
    )
  }

  def buildGuideStateChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, GuideConfigState]
  ): GuideConfigStatusChannels[F] = GuideConfigStatusChannels(
    new TestChannel[F, State, BinaryYesNo](s,
                                           l.andThen(Focus[GuideConfigState](_.pwfs1Integrating))
    ),
    new TestChannel[F, State, BinaryYesNo](s,
                                           l.andThen(Focus[GuideConfigState](_.pwfs2Integrating))
    ),
    new TestChannel[F, State, BinaryYesNo](s,
                                           l.andThen(Focus[GuideConfigState](_.oiwfsIntegrating))
    ),
    new TestChannel[F, State, BinaryOnOff](s, l.andThen(Focus[GuideConfigState](_.m2State))),
    new TestChannel[F, State, Int](s, l.andThen(Focus[GuideConfigState](_.absorbTipTilt))),
    new TestChannel[F, State, BinaryOnOff](s,
                                           l.andThen(Focus[GuideConfigState](_.m2ComaCorrection))
    ),
    new TestChannel[F, State, BinaryOnOff](s, l.andThen(Focus[GuideConfigState](_.m1State))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuideConfigState](_.m1Source))),
    new TestChannel[F, State, Double](s, l.andThen(Focus[GuideConfigState](_.p1ProbeGuide))),
    new TestChannel[F, State, Double](s, l.andThen(Focus[GuideConfigState](_.p2ProbeGuide))),
    new TestChannel[F, State, Double](s, l.andThen(Focus[GuideConfigState](_.oiProbeGuide))),
    new TestChannel[F, State, Double](s, l.andThen(Focus[GuideConfigState](_.p1ProbeGuided))),
    new TestChannel[F, State, Double](s, l.andThen(Focus[GuideConfigState](_.p2ProbeGuided))),
    new TestChannel[F, State, Double](s, l.andThen(Focus[GuideConfigState](_.oiProbeGuided))),
    new TestChannel[F, State, Double](s, l.andThen(Focus[GuideConfigState](_.mountP1Weight))),
    new TestChannel[F, State, Double](s, l.andThen(Focus[GuideConfigState](_.mountP2Weight))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuideConfigState](_.m2P1Guide))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuideConfigState](_.m2P2Guide))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuideConfigState](_.m2OiGuide))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuideConfigState](_.m2AoGuide)))
  )

  case class M2GuideConfigState(
    source:     TestChannel.State[String],
    samplefreq: TestChannel.State[String],
    filter:     TestChannel.State[String],
    freq1:      TestChannel.State[String],
    freq2:      TestChannel.State[String],
    beam:       TestChannel.State[String],
    reset:      TestChannel.State[String]
  )

  object M2GuideConfigState {
    val default: M2GuideConfigState = M2GuideConfigState(
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default
    )
  }

  def buildM2GuideConfigChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, M2GuideConfigState]
  ): M2GuideConfigChannels[F] = M2GuideConfigChannels(
    new TestChannel[F, State, String](s, l.andThen(Focus[M2GuideConfigState](_.source))),
    new TestChannel[F, State, String](s, l.andThen(Focus[M2GuideConfigState](_.samplefreq))),
    new TestChannel[F, State, String](s, l.andThen(Focus[M2GuideConfigState](_.filter))),
    new TestChannel[F, State, String](s, l.andThen(Focus[M2GuideConfigState](_.freq1))),
    new TestChannel[F, State, String](s, l.andThen(Focus[M2GuideConfigState](_.freq2))),
    new TestChannel[F, State, String](s, l.andThen(Focus[M2GuideConfigState](_.beam))),
    new TestChannel[F, State, String](s, l.andThen(Focus[M2GuideConfigState](_.reset)))
  )

  case class MountGuideState(
    mode:     TestChannel.State[String],
    source:   TestChannel.State[String],
    p1weight: TestChannel.State[String],
    p2weight: TestChannel.State[String]
  )

  object MountGuideState {
    val default: MountGuideState = MountGuideState(
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default
    )
  }

  def buildMountGuideChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, MountGuideState]
  ): MountGuideChannels[F] = MountGuideChannels(
    new TestChannel[F, State, String](s, l.andThen(Focus[MountGuideState](_.mode))),
    new TestChannel[F, State, String](s, l.andThen(Focus[MountGuideState](_.source))),
    new TestChannel[F, State, String](s, l.andThen(Focus[MountGuideState](_.p1weight))),
    new TestChannel[F, State, String](s, l.andThen(Focus[MountGuideState](_.p2weight)))
  )

  def buildWfsChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, WfsChannelState]
  ): WfsChannels[F] = WfsChannels(
    WfsObserveChannels(
      new TestChannel[F, State, String](
        s,
        l.andThen(Focus[WfsChannelState](_.observe.numberOfExposures))
      ),
      new TestChannel[F, State, String](s, l.andThen(Focus[WfsChannelState](_.observe.interval))),
      new TestChannel[F, State, String](s, l.andThen(Focus[WfsChannelState](_.observe.options))),
      new TestChannel[F, State, String](s, l.andThen(Focus[WfsChannelState](_.observe.label))),
      new TestChannel[F, State, String](s, l.andThen(Focus[WfsChannelState](_.observe.output))),
      new TestChannel[F, State, String](s, l.andThen(Focus[WfsChannelState](_.observe.path))),
      new TestChannel[F, State, String](s, l.andThen(Focus[WfsChannelState](_.observe.fileName)))
    ),
    new TestChannel[F, State, CadDirective](s, l.andThen(Focus[WfsChannelState](_.stop))),
    new TestChannel[F, State, String](s, l.andThen(Focus[WfsChannelState](_.signalProc))),
    new TestChannel[F, State, String](s, l.andThen(Focus[WfsChannelState](_.dark))),
    WfsClosedLoopChannels(
      new TestChannel[F, State, String](s, l.andThen(Focus[WfsChannelState](_.closedLoop.global))),
      new TestChannel[F, State, String](s, l.andThen(Focus[WfsChannelState](_.closedLoop.average))),
      new TestChannel[F, State, String](s,
                                        l.andThen(Focus[WfsChannelState](_.closedLoop.zernikes2m2))
      ),
      new TestChannel[F, State, String](s, l.andThen(Focus[WfsChannelState](_.closedLoop.mult)))
    )
  )

  def buildGuiderGainsChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, GuiderGainsState]
  ): GuiderGainsChannels[F] = GuiderGainsChannels(
    new TestChannel[F, State, String](s, l.andThen(Focus[GuiderGainsState](_.p1TipGain))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuiderGainsState](_.p1TiltGain))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuiderGainsState](_.p1FocusGain))),
    new TestChannel[F, State, BinaryYesNo](s, l.andThen(Focus[GuiderGainsState](_.p1Reset))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuiderGainsState](_.p2TipGain))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuiderGainsState](_.p2TiltGain))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuiderGainsState](_.p2FocusGain))),
    new TestChannel[F, State, BinaryYesNo](s, l.andThen(Focus[GuiderGainsState](_.p2Reset))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuiderGainsState](_.oiTipGain))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuiderGainsState](_.oiTiltGain))),
    new TestChannel[F, State, String](s, l.andThen(Focus[GuiderGainsState](_.oiFocusGain))),
    new TestChannel[F, State, BinaryYesNo](s, l.andThen(Focus[GuiderGainsState](_.oiReset)))
  )

  def buildGuideModeChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, ProbeGuideModeState]
  ): ProbeGuideModeChannels[F] = ProbeGuideModeChannels(
    new TestChannel[F, State, String](s, l.andThen(Focus[ProbeGuideModeState](_.state))),
    new TestChannel[F, State, String](s, l.andThen(Focus[ProbeGuideModeState](_.from))),
    new TestChannel[F, State, String](s, l.andThen(Focus[ProbeGuideModeState](_.to)))
  )

  def buildOiwfsSelectChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, OiwfsSelectState]
  ): OiwfsSelectChannels[F] = OiwfsSelectChannels(
    new TestChannel[F, State, String](s, l.andThen(Focus[OiwfsSelectState](_.oiwfsName))),
    new TestChannel[F, State, String](s, l.andThen(Focus[OiwfsSelectState](_.output)))
  )

  def buildChannels[F[_]: Applicative](s: Ref[F, State]): TcsChannels[F] =
    TcsChannels(
      telltale =
        TelltaleChannel[F]("dummy", new TestChannel[F, State, String](s, Focus[State](_.telltale))),
      pwfs1Telltale =
        TelltaleChannel[F]("dummy2",
                           new TestChannel[F, State, String](s, Focus[State](_.telltale))
        ),
      pwfs2Telltale =
        TelltaleChannel[F]("dummy3",
                           new TestChannel[F, State, String](s, Focus[State](_.telltale))
        ),
      oiwfsTelltale =
        TelltaleChannel[F]("dummy4",
                           new TestChannel[F, State, String](s, Focus[State](_.telltale))
        ),
      telescopeParkDir =
        new TestChannel[F, State, CadDirective](s, Focus[State](_.telescopeParkDir)),
      mountFollow = new TestChannel[F, State, String](s, Focus[State](_.mountFollow)),
      rotStopBrake = new TestChannel[F, State, String](s, Focus[State](_.rotStopBrake)),
      rotParkDir = new TestChannel[F, State, CadDirective](s, Focus[State](_.rotParkDir)),
      rotFollow = new TestChannel[F, State, String](s, Focus[State](_.rotFollow)),
      rotMoveAngle = new TestChannel[F, State, String](s, Focus[State](_.rotMoveAngle)),
      enclosure = buildEnclosureChannels(s),
      sourceA = buildTargetChannels(s, Focus[State](_.sourceA)),
      oiwfsTarget = buildTargetChannels(s, Focus[State](_.oiwfsTarget)),
      wavelSourceA = new TestChannel[F, State, String](s, Focus[State](_.rotMoveAngle)),
      slew = buildSlewChannels(s),
      rotator = buildRotatorChannels(s),
      origin = buildOriginChannels(s),
      focusOffset = new TestChannel[F, State, String](s, Focus[State](_.focusOffset)),
      oiProbeTracking = buildProbeTrackingChannels(s, Focus[State](_.oiwfsTracking)),
      oiProbe = buildProbeChannels(s, Focus[State](_.oiwfsProbe)),
      m1Guide = new TestChannel[F, State, String](s, Focus[State](_.m1Guide)),
      m1GuideConfig = buildM1GuideConfigChannels(s, Focus[State](_.m1GuideConfig)),
      m2Guide = new TestChannel[F, State, String](s, Focus[State](_.m2Guide)),
      m2GuideMode = new TestChannel[F, State, String](s, Focus[State](_.m2GuideMode)),
      m2GuideConfig = buildM2GuideConfigChannels(s, Focus[State](_.m2GuideConfig)),
      m2GuideReset = new TestChannel[F, State, CadDirective](s, Focus[State](_.m2GuideReset)),
      mountGuide = buildMountGuideChannels(s, Focus[State](_.mountGuide)),
      oiwfs = buildWfsChannels(s, Focus[State](_.oiWfs)),
      guide = buildGuideStateChannels(s, Focus[State](_.guideStatus)),
      guiderGains = buildGuiderGainsChannels(s, Focus[State](_.guiderGains)),
      probeGuideMode = buildGuideModeChannels(s, Focus[State](_.probeGuideMode)),
      oiwfsSelect = buildOiwfsSelectChannels(s, Focus[State](_.oiwfsSelect))
    )

  def build[F[_]: Monad: Parallel](s: Ref[F, State]): TcsEpicsSystem[F] =
    TcsEpicsSystem.buildSystem(new TestApplyCommand[F], buildChannels(s))

}
