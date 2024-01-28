// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.Monad
import cats.Parallel
import cats.effect.Resource
import cats.effect.Temporal
import cats.effect.std.Dispatcher
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.ags.GuideProbe
import lucuma.core.math.Angle
import mouse.all.*
import navigate.epics.Channel
import navigate.epics.EpicsService
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.VerifiedEpics
import navigate.epics.VerifiedEpics.*
import navigate.model.Distance
import navigate.model.enums.DomeMode
import navigate.model.enums.ShutterMode
import navigate.server.ApplyCommandResult
import navigate.server.acm.CadDirective
import navigate.server.acm.Encoder
import navigate.server.acm.Encoder.given
import navigate.server.acm.GeminiApplyCommand
import navigate.server.acm.ParameterList.*
import navigate.server.acm.writeCadParam
import navigate.server.epicsdata.BinaryOnOff
import navigate.server.epicsdata.BinaryOnOff.given
import navigate.server.epicsdata.BinaryYesNo
import navigate.server.epicsdata.BinaryYesNo.given
import navigate.server.tcs.TcsEpicsSystem.TcsStatus

import scala.concurrent.duration.FiniteDuration

import TcsChannels.{ProbeChannels, ProbeTrackingChannels, SlewChannels, TargetChannels, WfsChannels}

trait TcsEpicsSystem[F[_]] {
  // TcsCommands accumulates the list of channels that need to be written to set parameters.
  // Once all the parameters are defined, the user calls the post method. Only then the EPICS channels will be verified,
  // the parameters written, and the apply record triggered.
  def startCommand(timeout: FiniteDuration): TcsEpicsSystem.TcsCommands[F]

  val status: TcsStatus[F]
}

object TcsEpicsSystem {

  trait TcsEpics[F[_]] {
    def post(timeout: FiniteDuration): VerifiedEpics[F, F, ApplyCommandResult]

    val mountParkCmd: ParameterlessCommandChannels[F]
    val mountFollowCmd: Command1Channels[F, BinaryOnOff]
    val rotStopCmd: Command1Channels[F, BinaryYesNo]
    val rotParkCmd: ParameterlessCommandChannels[F]
    val rotFollowCmd: Command1Channels[F, BinaryOnOff]
    val rotMoveCmd: Command1Channels[F, Double]
    val carouselModeCmd: Command5Channels[F, String, String, Double, BinaryOnOff, BinaryOnOff]
    val carouselMoveCmd: Command1Channels[F, Double]
    val shuttersMoveCmd: Command2Channels[F, Double, Double]
    val ventGatesMoveCmd: Command2Channels[F, Double, Double]
    val sourceACmd: TargetCommandChannels[F]
    val oiwfsTargetCmd: TargetCommandChannels[F]
    val wavelSourceA: Command1Channels[F, Double]
    val slewCmd: SlewCommandChannels[F]
    val rotatorConfigCmd: Command4Channels[F, Double, String, String, Double]
    val originCmd: Command6Channels[F, Double, Double, Double, Double, Double, Double]
    val focusOffsetCmd: Command1Channels[F, Double]
    val oiwfsProbeTrackingCmd: ProbeTrackingCommandChannels[F]
    val oiwfsProbeCmds: ProbeCommandsChannels[F]
    val oiWfsCmds: WfsCommandsChannels[F]
    val m1GuideConfigCmd: Command4Channels[F, String, String, Int, String]
    val m1GuideCmd: Command1Channels[F, BinaryOnOff]
    val m2GuideCmd: Command1Channels[F, BinaryOnOff]
    val m2GuideModeCmd: Command1Channels[F, BinaryOnOff]
    val m2GuideConfigCmd: Command7Channels[F,
                                           String,
                                           Double,
                                           String,
                                           Double,
                                           Double,
                                           String,
                                           BinaryOnOff
    ]
    val m2GuideResetCmd: ParameterlessCommandChannels[F]
    val mountGuideCmd: Command4Channels[F, BinaryOnOff, String, Double, Double]
    val p1GuiderGainsCmd: Command3Channels[F, Double, Double, Double]
    val p2GuiderGainsCmd: Command3Channels[F, Double, Double, Double]
    val oiGuiderGainsCmd: Command3Channels[F, Double, Double, Double]
    val guideModeCmd: Command3Channels[F, BinaryOnOff, GuideProbe, GuideProbe]

    // val offsetACmd: OffsetCmd[F]
    // val offsetBCmd: OffsetCmd[F]
    // val wavelSourceB: TargetWavelengthCmd[F]
    // val m2Beam: M2Beam[F]
    // val pwfs1ProbeGuideCmd: ProbeGuideCmd[F]
    // val pwfs2ProbeGuideCmd: ProbeGuideCmd[F]
    // val pwfs1ProbeFollowCmd: ProbeFollowCmd[F]
    // val pwfs2ProbeFollowCmd: ProbeFollowCmd[F]
    // val aoProbeFollowCmd: ProbeFollowCmd[F]
    // val pwfs1Park: EpicsCommand[F]
    // val pwfs2Park: EpicsCommand[F]
    // val pwfs1StopObserveCmd: EpicsCommand[F]
    // val pwfs2StopObserveCmd: EpicsCommand[F]
    // val oiwfsStopObserveCmd: EpicsCommand[F]
    // val pwfs1ObserveCmd: WfsObserveCmd[F]
    // val pwfs2ObserveCmd: WfsObserveCmd[F]
    // val oiwfsObserveCmd: WfsObserveCmd[F]
    // val hrwfsParkCmd: EpicsCommand[F]
    // val hrwfsPosCmd: HrwfsPosCmd[F]
    // val scienceFoldParkCmd: EpicsCommand[F]
    // val scienceFoldPosCmd: ScienceFoldPosCmd[F]
    // val observe: EpicsCommand[F]
    // val endObserve: EpicsCommand[F]
    // // GeMS Commands
    // import VirtualGemsTelescope._
    // val g1ProbeGuideCmd: ProbeGuideCmd[F]
    // val g2ProbeGuideCmd: ProbeGuideCmd[F]
    // val g3ProbeGuideCmd: ProbeGuideCmd[F]
    // val g4ProbeGuideCmd: ProbeGuideCmd[F]
    // def gemsProbeGuideCmd(g: VirtualGemsTelescope): ProbeGuideCmd[F] = g match {
    //   case G1 => g1ProbeGuideCmd
    //   case G2 => g2ProbeGuideCmd
    //   case G3 => g3ProbeGuideCmd
    //   case G4 => g4ProbeGuideCmd
    // }
    // val wavelG1: TargetWavelengthCmd[F]
    // val wavelG2: TargetWavelengthCmd[F]
    // val wavelG3: TargetWavelengthCmd[F]
    // val wavelG4: TargetWavelengthCmd[F]
    // def gemsWavelengthCmd(g: VirtualGemsTelescope): TargetWavelengthCmd[F] = g match {
    //   case G1 => wavelG1
    //   case G2 => wavelG2
    //   case G3 => wavelG3
    //   case G4 => wavelG4
    // }
    // val cwfs1ProbeFollowCmd: ProbeFollowCmd[F]
    // val cwfs2ProbeFollowCmd: ProbeFollowCmd[F]
    // val cwfs3ProbeFollowCmd: ProbeFollowCmd[F]
    // val odgw1FollowCmd: ProbeFollowCmd[F]
    // val odgw2FollowCmd: ProbeFollowCmd[F]
    // val odgw3FollowCmd: ProbeFollowCmd[F]
    // val odgw4FollowCmd: ProbeFollowCmd[F]
    // val odgw1ParkCmd: EpicsCommand[F]
    // val odgw2ParkCmd: EpicsCommand[F]
    // val odgw3ParkCmd: EpicsCommand[F]
    // val odgw4ParkCmd: EpicsCommand[F]
  }

  trait TcsStatus[F[_]] {
    // val aoCorrect: AoCorrect[F]
    // val aoPrepareControlMatrix: AoPrepareControlMatrix[F]
    // val aoFlatten: EpicsCommand[F]
    // val aoStatistics: AoStatistics[F]
    // val targetFilter: TargetFilter[F]
    def absorbTipTilt: VerifiedEpics[F, F, Int]
    def m1GuideSource: VerifiedEpics[F, F, String]
    def m1Guide: VerifiedEpics[F, F, BinaryOnOff]
    def m2p1Guide: VerifiedEpics[F, F, String]
    def m2p2Guide: VerifiedEpics[F, F, String]
    def m2oiGuide: VerifiedEpics[F, F, String]
    def m2aoGuide: VerifiedEpics[F, F, String]
    def comaCorrect: VerifiedEpics[F, F, BinaryOnOff]
    def m2GuideState: VerifiedEpics[F, F, BinaryOnOff]
    // def xoffsetPoA1: F[Double]
    // def yoffsetPoA1: F[Double]
    // def xoffsetPoB1: F[Double]
    // def yoffsetPoB1: F[Double]
    // def xoffsetPoC1: F[Double]
    // def yoffsetPoC1: F[Double]
    // def sourceAWavelength: F[Double]
    // def sourceBWavelength: F[Double]
    // def sourceCWavelength: F[Double]
    // def chopBeam: F[String]
    // def p1FollowS: F[String]
    // def p2FollowS: F[String]
    // def oiFollowS: F[String]
    // def aoFollowS: F[String]
    // def p1Parked: F[Boolean]
    // def p2Parked: F[Boolean]
    // def oiName: F[String]
    // def oiParked: F[Boolean]
    // def pwfs1On: F[BinaryYesNo]
    // def pwfs2On: F[BinaryYesNo]
    // def oiwfsOn: F[BinaryYesNo]
    // def sfName: F[String]
    // def sfParked: F[Int]
    // def agHwName: F[String]
    // def agHwParked: F[Int]
    // def instrAA: F[Double]
    // def inPosition: F[String]
    // def agInPosition: F[Double]
    // val pwfs1ProbeGuideConfig: ProbeGuideConfig[F]
    // val pwfs2ProbeGuideConfig: ProbeGuideConfig[F]
    // val oiwfsProbeGuideConfig: ProbeGuideConfig[F]
    // // This functions returns a F that, when run, first waits tcsSettleTime to absorb in-position transients, then waits
    // // for the in-position to change to true and stay true for stabilizationTime. It will wait up to `timeout`
    // // seconds for that to happen.
    // def waitInPosition(stabilizationTime: Duration, timeout: FiniteDuration)(using
    //   T:                                  Timer[F]
    // ): F[Unit]
    // // `waitAGInPosition` works like `waitInPosition`, but for the AG in-position flag.
    // /* TODO: AG inposition can take up to 1[s] to react to a TCS command. If the value is read before that, it may induce
    //   * an error. A better solution is to detect the edge, from not in position to in-position.
    //   */
    // def waitAGInPosition(timeout: FiniteDuration)(using T: Timer[F]): F[Unit]
    // def hourAngle: F[String]
    // def localTime: F[String]
    // def trackingFrame: F[String]
    // def trackingEpoch: F[Double]
    // def equinox: F[Double]
    // def trackingEquinox: F[String]
    // def trackingDec: F[Double]
    // def trackingRA: F[Double]
    // def elevation: F[Double]
    // def azimuth: F[Double]
    // def crPositionAngle: F[Double]
    // def ut: F[String]
    // def date: F[String]
    // def m2Baffle: F[String]
    // def m2CentralBaffle: F[String]
    // def st: F[String]
    // def sfRotation: F[Double]
    // def sfTilt: F[Double]
    // def sfLinear: F[Double]
    // def instrPA: F[Double]
    // def targetA: F[List[Double]]
    // def aoFoldPosition: F[String]
    // def useAo: F[BinaryYesNo]
    // def airmass: F[Double]
    // def airmassStart: F[Double]
    // def airmassEnd: F[Double]
    // def carouselMode: F[String]
    // def crFollow: F[Int]
    // def crTrackingFrame: F[String]
    // def sourceATarget: Target[F]
    // val pwfs1Target: Target[F]
    // val pwfs2Target: Target[F]
    // val oiwfsTarget: Target[F]
    // def parallacticAngle: F[Angle]
    // def m2UserFocusOffset: F[Double]
    // def pwfs1IntegrationTime: F[Double]
    // def pwfs2IntegrationTime: F[Double]
    // // Attribute must be changed back to Double after EPICS channel is fixed.
    // def oiwfsIntegrationTime: F[Double]
    // def gsaoiPort: F[Int]
    // def gpiPort: F[Int]
    // def f2Port: F[Int]
    // def niriPort: F[Int]
    // def gnirsPort: F[Int]
    // def nifsPort: F[Int]
    // def gmosPort: F[Int]
    // def ghostPort: F[Int]
    // def aoGuideStarX: F[Double]
    // def aoGuideStarY: F[Double]
    // def aoPreparedCMX: F[Double]
    // def aoPreparedCMY: F[Double]
    // def gwfs1Target: Target[F]
    // def gwfs2Target: Target[F]
    // def gwfs3Target: Target[F]
    // def gwfs4Target: Target[F]
    // def gemsTarget(g: VirtualGemsTelescope): Target[F] = g match {
    //   case G1 => gwfs1Target
    //   case G2 => gwfs2Target
    //   case G3 => gwfs3Target
    //   case G4 => gwfs4Target
    // }
    // // GeMS statuses
    // def cwfs1Follow: F[Boolean]
    // def cwfs2Follow: F[Boolean]
    // def cwfs3Follow: F[Boolean]
    // def odgw1Follow: F[Boolean]
    // def odgw2Follow: F[Boolean]
    // def odgw3Follow: F[Boolean]
    // def odgw4Follow: F[Boolean]
    // def odgw1Parked: F[Boolean]
    // def odgw2Parked: F[Boolean]
    // def odgw3Parked: F[Boolean]
    // def odgw4Parked: F[Boolean]
    // def g1MapName: F[Option[GemsSource]]
    // def g2MapName: F[Option[GemsSource]]
    // def g3MapName: F[Option[GemsSource]]
    // def g4MapName: F[Option[GemsSource]]
    // def g1Wavelength: F[Double]
    // def g2Wavelength: F[Double]
    // def g3Wavelength: F[Double]
    // def g4Wavelength: F[Double]
    // def gemsWavelength(g: VirtualGemsTelescope): F[Double] = g match {
    //   case G1 => g1Wavelength
    //   case G2 => g2Wavelength
    //   case G3 => g3Wavelength
    //   case G4 => g4Wavelength
    // }
    // val g1GuideConfig: ProbeGuideConfig[F]
    // val g2GuideConfig: ProbeGuideConfig[F]
    // val g3GuideConfig: ProbeGuideConfig[F]
    // val g4GuideConfig: ProbeGuideConfig[F]
    // def gemsGuideConfig(g: VirtualGemsTelescope): ProbeGuideConfig[F] = g match {
    //   case G1 => g1GuideConfig
    //   case G2 => g2GuideConfig
    //   case G3 => g3GuideConfig
    //   case G4 => g4GuideConfig
    // }
  }

  object TcsStatus {

    def build[F[_]](channels: TcsChannels[F]): TcsStatus[F] =
      new TcsStatus[F] {
        override def absorbTipTilt: VerifiedEpics[F, F, Int]        =
          VerifiedEpics.readChannel(channels.telltale, channels.guide.absorbTipTilt)
        override def m1GuideSource: VerifiedEpics[F, F, String]     =
          VerifiedEpics.readChannel(channels.telltale, channels.guide.m1Source)
        override def m1Guide: VerifiedEpics[F, F, BinaryOnOff]      =
          VerifiedEpics.readChannel(channels.telltale, channels.guide.m1State)
        override def m2p1Guide: VerifiedEpics[F, F, String]         =
          VerifiedEpics.readChannel(channels.telltale, channels.guide.m2P1Guide)
        override def m2p2Guide: VerifiedEpics[F, F, String]         =
          VerifiedEpics.readChannel(channels.telltale, channels.guide.m2P2Guide)
        override def m2oiGuide: VerifiedEpics[F, F, String]         =
          VerifiedEpics.readChannel(channels.telltale, channels.guide.m2OiGuide)
        override def m2aoGuide: VerifiedEpics[F, F, String]         =
          VerifiedEpics.readChannel(channels.telltale, channels.guide.m2AoGuide)
        override def comaCorrect: VerifiedEpics[F, F, BinaryOnOff]  =
          VerifiedEpics.readChannel(channels.telltale, channels.guide.m2ComaCorrection)
        override def m2GuideState: VerifiedEpics[F, F, BinaryOnOff] =
          VerifiedEpics.readChannel(channels.telltale, channels.guide.m2State)
      }
  }

  val className: String = getClass.getName

  private[tcs] def buildSystem[F[_]: Monad: Parallel](
    applyCmd: GeminiApplyCommand[F],
    channels: TcsChannels[F]
  ): TcsEpicsSystem[F] =
    new TcsEpicsSystemImpl[F](new TcsEpicsImpl[F](applyCmd, channels), TcsStatus.build(channels))

  def build[F[_]: Dispatcher: Temporal: Parallel](
    service: EpicsService[F],
    tops:    Map[String, String]
  ): Resource[F, TcsEpicsSystem[F]] = {
    def readTop(key: String): NonEmptyString =
      tops
        .get(key)
        .flatMap(NonEmptyString.from(_).toOption)
        .getOrElse(NonEmptyString.unsafeFrom(s"${key}:"))

    val top   = readTop("tcs")
    val pwfs1 = readTop("pwfs1")
    val pwfs2 = readTop("pwfs2")
    val oi    = readTop("oiwfs")
    val ag    = readTop("ag")

    for {
      channels <- TcsChannels.buildChannels(service,
                                            TcsTop(top),
                                            Pwfs1Top(pwfs1),
                                            Pwfs2Top(pwfs2),
                                            OiwfsTop(oi),
                                            AgTop(ag)
                  )
      applyCmd <-
        GeminiApplyCommand.build(service, channels.telltale, s"${top}apply", s"${top}applyC")
    } yield buildSystem(applyCmd, channels)
  }

  case class TcsCommandsImpl[F[_]: Monad: Parallel](
    tcsEpics: TcsEpics[F],
    timeout:  FiniteDuration,
    params:   ParameterList[F]
  ) extends TcsCommands[F] {

    private def addParam(p: VerifiedEpics[F, F, Unit]): TcsCommands[F] =
      TcsCommandsImpl(tcsEpics, timeout, params :+ p)

    private def addMultipleParams(c: ParameterList[F]): TcsCommands[F] =
      TcsCommandsImpl(tcsEpics, timeout, params ++ c)

    override def post: VerifiedEpics[F, F, ApplyCommandResult] =
      params.compile *> tcsEpics.post(timeout)

    override val mcsParkCommand: BaseCommand[F, TcsCommands[F]] =
      new BaseCommand[F, TcsCommands[F]] {
        override def mark: TcsCommands[F] = addParam(tcsEpics.mountParkCmd.mark)
      }

    override val mcsFollowCommand: FollowCommand[F, TcsCommands[F]] =
      (enable: Boolean) =>
        addParam(
          tcsEpics.mountFollowCmd.setParam1(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

    override val rotStopCommand: RotStopCommand[F, TcsCommands[F]] =
      (enable: Boolean) =>
        addParam(
          tcsEpics.rotStopCmd.setParam1(enable.fold(BinaryYesNo.Yes, BinaryYesNo.No))
        )

    override val rotParkCommand: BaseCommand[F, TcsCommands[F]] =
      new BaseCommand[F, TcsCommands[F]] {
        override def mark: TcsCommands[F] = addParam(tcsEpics.rotParkCmd.mark)
      }

    override val rotFollowCommand: FollowCommand[F, TcsCommands[F]] =
      (enable: Boolean) =>
        addParam(
          tcsEpics.rotFollowCmd.setParam1(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

    override val rotMoveCommand: RotMoveCommand[F, TcsCommands[F]] =
      (angle: Angle) =>
        addParam(
          tcsEpics.rotMoveCmd.setParam1(angle.toDoubleDegrees)
        )

    override val ecsCarouselModeCmd: CarouselModeCommand[F, TcsCommands[F]] =
      new CarouselModeCommand[F, TcsCommands[F]] {
        override def setDomeMode(mode: DomeMode): TcsCommands[F] = addParam(
          tcsEpics.carouselModeCmd.setParam1(mode.tag)
        )

        override def setShutterMode(mode: ShutterMode): TcsCommands[F] = addParam(
          tcsEpics.carouselModeCmd.setParam2(mode.tag)
        )

        override def setSlitHeight(height: Double): TcsCommands[F] = addParam(
          tcsEpics.carouselModeCmd.setParam3(height)
        )

        override def setDomeEnable(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.carouselModeCmd.setParam4(
            enable.fold[BinaryOnOff](BinaryOnOff.On, BinaryOnOff.Off)
          )
        )

        override def setShutterEnable(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.carouselModeCmd.setParam5(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )
      }

    override val ecsCarouselMoveCmd: CarouselMoveCommand[F, TcsCommands[F]] =
      (angle: Angle) =>
        addParam(
          tcsEpics.carouselMoveCmd.setParam1(angle.toDoubleDegrees)
        )

    override val ecsShuttersMoveCmd: ShuttersMoveCommand[F, TcsCommands[F]] =
      new ShuttersMoveCommand[F, TcsCommands[F]] {
        override def setTop(pos: Double): TcsCommands[F] = addParam(
          tcsEpics.shuttersMoveCmd.setParam1(pos)
        )

        override def setBottom(pos: Double): TcsCommands[F] = addParam(
          tcsEpics.shuttersMoveCmd.setParam2(pos)
        )
      }

    override val ecsVenGatesMoveCmd: VentGatesMoveCommand[F, TcsCommands[F]] =
      new VentGatesMoveCommand[F, TcsCommands[F]] {
        override def setVentGateEast(pos: Double): TcsCommands[F] = addParam(
          tcsEpics.ventGatesMoveCmd.setParam1(pos)
        )

        override def setVentGateWest(pos: Double): TcsCommands[F] = addParam(
          tcsEpics.ventGatesMoveCmd.setParam2(pos)
        )
      }
    override val sourceACmd: TargetCommand[F, TcsCommands[F]]                =
      new TargetCommand[F, TcsCommands[F]] {
        override def objectName(v: String): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.objectName(v)
        )

        override def coordSystem(v: String): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.coordSystem(v)
        )

        override def coord1(v: Double): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.coord1(v)
        )

        override def coord2(v: Double): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.coord2(v)
        )

        override def epoch(v: Double): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.epoch(v)
        )

        override def equinox(v: String): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.equinox(v)
        )

        override def parallax(v: Double): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.parallax(v)
        )

        override def properMotion1(v: Double): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.properMotion1(v)
        )

        override def properMotion2(v: Double): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.properMotion2(v)
        )

        override def radialVelocity(v: Double): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.radialVelocity(v)
        )

        override def brightness(v: Double): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.brightness(v)
        )

        override def ephemerisFile(v: String): TcsCommands[F] = addParam(
          tcsEpics.sourceACmd.ephemerisFile(v)
        )
      }

    override val oiwfsTargetCmd: TargetCommand[F, TcsCommands[F]] =
      new TargetCommand[F, TcsCommands[F]] {
        override def objectName(v: String): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.objectName(v)
        )

        override def coordSystem(v: String): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.coordSystem(v)
        )

        override def coord1(v: Double): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.coord1(v)
        )

        override def coord2(v: Double): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.coord2(v)
        )

        override def epoch(v: Double): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.epoch(v)
        )

        override def equinox(v: String): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.equinox(v)
        )

        override def parallax(v: Double): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.parallax(v)
        )

        override def properMotion1(v: Double): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.properMotion1(v)
        )

        override def properMotion2(v: Double): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.properMotion2(v)
        )

        override def radialVelocity(v: Double): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.radialVelocity(v)
        )

        override def brightness(v: Double): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.brightness(v)
        )

        override def ephemerisFile(v: String): TcsCommands[F] = addParam(
          tcsEpics.oiwfsTargetCmd.ephemerisFile(v)
        )
      }

    override val sourceAWavel: WavelengthCommand[F, TcsCommands[F]] = { (v: Double) =>
      addParam(tcsEpics.wavelSourceA.setParam1(v))
    }

    override val slewOptionsCommand: SlewOptionsCommand[F, TcsCommands[F]] =
      new SlewOptionsCommand[F, TcsCommands[F]] {
        override def zeroChopThrow(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.zeroChopThrow(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def zeroSourceOffset(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.zeroSourceOffset(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def zeroSourceDiffTrack(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.zeroSourceDiffTrack(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def zeroMountOffset(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.zeroMountOffset(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def zeroMountDiffTrack(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.zeroMountDiffTrack(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def shortcircuitTargetFilter(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.shortcircuitTargetFilter(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def shortcircuitMountFilter(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.shortcircuitMountFilter(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def resetPointing(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.resetPointing(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def stopGuide(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.stopGuide(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def zeroGuideOffset(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.zeroGuideOffset(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def zeroInstrumentOffset(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.zeroInstrumentOffset(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def autoparkPwfs1(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.autoparkPwfs1(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def autoparkPwfs2(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.autoparkPwfs2(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def autoparkOiwfs(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.autoparkOiwfs(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def autoparkGems(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.autoparkGems(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def autoparkAowfs(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.slewCmd.autoparkAowfs(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )
      }
    override val rotatorCommand: RotatorCommand[F, TcsCommands[F]]         =
      new RotatorCommand[F, TcsCommands[F]] {
        override def ipa(v: Angle): TcsCommands[F] = addParam(
          tcsEpics.rotatorConfigCmd.setParam1(v.toDoubleDegrees)
        )

        override def system(v: String): TcsCommands[F] = addParam(
          tcsEpics.rotatorConfigCmd.setParam2(v)
        )

        override def equinox(v: String): TcsCommands[F] = addParam(
          tcsEpics.rotatorConfigCmd.setParam3(v)
        )

        override def iaa(v: Angle): TcsCommands[F] = addParam(
          tcsEpics.rotatorConfigCmd.setParam4(v.toDoubleDegrees)
        )
      }

    override val originCommand: OriginCommand[F, TcsCommands[F]] =
      new OriginCommand[F, TcsCommands[F]] {
        override def originX(v: Distance): TcsCommands[F] =
          addMultipleParams(
            List(
              tcsEpics.originCmd.setParam1(v.toMillimeters.value.toDouble),
              tcsEpics.originCmd.setParam3(v.toMillimeters.value.toDouble),
              tcsEpics.originCmd.setParam5(v.toMillimeters.value.toDouble)
            )
          )

        override def originY(v: Distance): TcsCommands[F] =
          addMultipleParams(
            List(
              tcsEpics.originCmd.setParam2(v.toMillimeters.value.toDouble),
              tcsEpics.originCmd.setParam4(v.toMillimeters.value.toDouble),
              tcsEpics.originCmd.setParam6(v.toMillimeters.value.toDouble)
            )
          )
      }

    override val focusOffsetCommand: FocusOffsetCommand[F, TcsCommands[F]] =
      new FocusOffsetCommand[F, TcsCommands[F]] {
        override def focusOffset(v: Distance): TcsCommands[F] = addParam(
          // En que formato recibe Epics este valor Milimetros, Metros, Micrometros????
          tcsEpics.focusOffsetCmd.setParam1(v.toMillimeters.value.toDouble)
        )
      }

    override val oiwfsProbeTrackingCommand: ProbeTrackingCommand[F, TcsCommands[F]] =
      new ProbeTrackingCommand[F, TcsCommands[F]] {
        override def nodAchopA(v: Boolean): TcsCommands[F] = addParam(
          tcsEpics.oiwfsProbeTrackingCmd.nodAchopA(v.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def nodAchopB(v: Boolean): TcsCommands[F] = addParam(
          tcsEpics.oiwfsProbeTrackingCmd.nodAchopB(v.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def nodBchopA(v: Boolean): TcsCommands[F] = addParam(
          tcsEpics.oiwfsProbeTrackingCmd.nodBchopA(v.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def nodBchopB(v: Boolean): TcsCommands[F] = addParam(
          tcsEpics.oiwfsProbeTrackingCmd.nodBchopB(v.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )
      }
    override val oiwfsProbeCommands: ProbeCommands[F, TcsCommands[F]]               =
      new ProbeCommands[F, TcsCommands[F]] {
        override val park: BaseCommand[F, TcsCommands[F]] = new BaseCommand[F, TcsCommands[F]] {
          override def mark: TcsCommands[F] = addParam(tcsEpics.oiwfsProbeCmds.park.mark)
        }

        override val follow: FollowCommand[F, TcsCommands[F]] =
          (enable: Boolean) =>
            addParam(
              tcsEpics.oiwfsProbeCmds.follow.setParam1(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
            )
      }

    override val m1GuideCommand: GuideCommand[F, TcsCommands[F]] =
      (enable: Boolean) =>
        addParam(
          tcsEpics.m1GuideCmd.setParam1(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

    override val m1GuideConfigCommand: M1GuideConfigCommand[F, TcsCommands[F]] =
      new M1GuideConfigCommand[F, TcsCommands[F]] {
        override def weighting(v: String): TcsCommands[F] = addParam(
          tcsEpics.m1GuideConfigCmd.setParam1(v)
        )

        override def source(v: String): TcsCommands[F] = addParam(
          tcsEpics.m1GuideConfigCmd.setParam2(v)
        )

        override def frames(v: Int): TcsCommands[F] = addParam(
          tcsEpics.m1GuideConfigCmd.setParam3(v)
        )

        override def filename(v: String): TcsCommands[F] = addParam(
          tcsEpics.m1GuideConfigCmd.setParam4(v)
        )
      }

    override val m2GuideCommand: GuideCommand[F, TcsCommands[F]] =
      (enable: Boolean) =>
        addParam(
          tcsEpics.m2GuideCmd.setParam1(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

    override val m2GuideModeCommand: M2GuideModeCommand[F, TcsCommands[F]] =
      (enable: Boolean) =>
        addParam(
          tcsEpics.m2GuideModeCmd.setParam1(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

    override val m2GuideConfigCommand: M2GuideConfigCommand[F, TcsCommands[F]] =
      new M2GuideConfigCommand[F, TcsCommands[F]] {
        override def source(v: String): TcsCommands[F] = addParam(
          tcsEpics.m2GuideConfigCmd.setParam1(v)
        )

        override def sampleFreq(v: Double): TcsCommands[F] = addParam(
          tcsEpics.m2GuideConfigCmd.setParam2(v)
        )

        override def filter(v: String): TcsCommands[F] = addParam(
          tcsEpics.m2GuideConfigCmd.setParam3(v)
        )

        override def freq1(v: Double): TcsCommands[F] = addParam(
          tcsEpics.m2GuideConfigCmd.setParam4(v)
        )

        override def freq2(v: Double): TcsCommands[F] = addParam(
          tcsEpics.m2GuideConfigCmd.setParam5(v)
        )

        override def beam(v: String): TcsCommands[F] = addParam(
          tcsEpics.m2GuideConfigCmd.setParam6(v)
        )

        override def reset(v: Boolean): TcsCommands[F] = addParam(
          tcsEpics.m2GuideConfigCmd.setParam7(v.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )
      }

    override val m2GuideResetCommand: BaseCommand[F, TcsCommands[F]] =
      new BaseCommand[F, TcsCommands[F]] {
        override def mark: TcsCommands[F] = addParam(tcsEpics.m2GuideResetCmd.mark)
      }

    override val mountGuideCommand: MountGuideCommand[F, TcsCommands[F]] =
      new MountGuideCommand[F, TcsCommands[F]] {
        override def mode(v: Boolean): TcsCommands[F] = addParam(
          tcsEpics.mountGuideCmd.setParam1(v.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def source(v: String): TcsCommands[F] = addParam(
          tcsEpics.mountGuideCmd.setParam2(v)
        )

        override def p1Weight(v: Double): TcsCommands[F] = addParam(
          tcsEpics.mountGuideCmd.setParam3(v)
        )

        override def p2Weight(v: Double): TcsCommands[F] = addParam(
          tcsEpics.mountGuideCmd.setParam4(v)
        )
      }

    override val oiWfsCommands: WfsCommands[F, TcsCommands[F]] =
      new WfsCommands[F, TcsCommands[F]] {
        override val observe: WfsObserveCommand[F, TcsCommands[F]]             =
          new WfsObserveCommand[F, TcsCommands[F]] {
            override def numberOfExposures(v: Int): TcsCommands[F] = addParam(
              tcsEpics.oiWfsCmds.observe.setParam1(v)
            )
            override def interval(v: Double): TcsCommands[F]       = addParam(
              tcsEpics.oiWfsCmds.observe.setParam2(v)
            )
            override def options(v: String): TcsCommands[F]        = addParam(
              tcsEpics.oiWfsCmds.observe.setParam3(v)
            )
            override def label(v: String): TcsCommands[F]          = addParam(
              tcsEpics.oiWfsCmds.observe.setParam4(v)
            )
            override def output(v: String): TcsCommands[F]         = addParam(
              tcsEpics.oiWfsCmds.observe.setParam5(v)
            )
            override def path(v: String): TcsCommands[F]           = addParam(
              tcsEpics.oiWfsCmds.observe.setParam6(v)
            )
            override def fileName(v: String): TcsCommands[F]       = addParam(
              tcsEpics.oiWfsCmds.observe.setParam7(v)
            )
          }
        override val stop: BaseCommand[F, TcsCommands[F]]                      = new BaseCommand[F, TcsCommands[F]] {
          override def mark: TcsCommands[F] = addParam(tcsEpics.oiWfsCmds.stop.mark)
        }
        override val signalProc: WfsSignalProcConfigCommand[F, TcsCommands[F]] = (v: String) =>
          addParam(tcsEpics.oiWfsCmds.signalProc.setParam1(v))
        override val dark: WfsDarkCommand[F, TcsCommands[F]]                   = (v: String) =>
          addParam(tcsEpics.oiWfsCmds.dark.setParam1(v))
        override val closedLoop: WfsClosedLoopCommand[F, TcsCommands[F]]       =
          new WfsClosedLoopCommand[F, TcsCommands[F]] {
            override def global(v: Double): TcsCommands[F] = addParam(
              tcsEpics.oiWfsCmds.closedLoop.setParam1(v)
            )

            override def average(v: Int): TcsCommands[F] = addParam(
              tcsEpics.oiWfsCmds.closedLoop.setParam2(v)
            )

            override def zernikes2m2(v: Int): TcsCommands[F] = addParam(
              tcsEpics.oiWfsCmds.closedLoop.setParam3(v)
            )

            override def mult(v: Double): TcsCommands[F] = addParam(
              tcsEpics.oiWfsCmds.closedLoop.setParam4(v)
            )
          }
      }

    override val guiderGainsCommands: GuiderGainsCommand[F, TcsCommands[F]] =
      new GuiderGainsCommand[F, TcsCommands[F]] {
        override def dayTimeGains: TcsCommands[F] =
          addMultipleParams(
            List(
              tcsEpics.p1GuiderGainsCmd.setParam1(0.0),
              tcsEpics.p1GuiderGainsCmd.setParam2(0.0),
              tcsEpics.p1GuiderGainsCmd.setParam3(0.0),
              tcsEpics.p2GuiderGainsCmd.setParam1(0.0),
              tcsEpics.p2GuiderGainsCmd.setParam2(0.0),
              tcsEpics.p2GuiderGainsCmd.setParam3(0.0),
              tcsEpics.oiGuiderGainsCmd.setParam1(0.0),
              tcsEpics.oiGuiderGainsCmd.setParam2(0.0),
              tcsEpics.oiGuiderGainsCmd.setParam3(0.0)
            )
          )

        // TODO These hardcoded value should be configurable
        override def defaultGains: TcsCommands[F] =
          addMultipleParams(
            List(
              tcsEpics.p1GuiderGainsCmd.setParam1(0.03),
              tcsEpics.p1GuiderGainsCmd.setParam2(0.03),
              tcsEpics.p1GuiderGainsCmd.setParam3(0.00002),
              tcsEpics.p2GuiderGainsCmd.setParam1(0.05),
              tcsEpics.p2GuiderGainsCmd.setParam2(0.05),
              tcsEpics.p2GuiderGainsCmd.setParam3(0.0001),
              tcsEpics.oiGuiderGainsCmd.setParam1(0.08),
              tcsEpics.oiGuiderGainsCmd.setParam2(0.08),
              tcsEpics.oiGuiderGainsCmd.setParam3(0.00015)
            )
          )

      }

    override val guideModeCommand: GuideModeCommand[F, TcsCommands[F]] =
      new GuideModeCommand[F, TcsCommands[F]] {
        override def setMode(pg: Option[ProbeGuide]): TcsCommands[F] =
          pg.fold(
            addParam(tcsEpics.guideModeCmd.setParam1(BinaryOnOff.Off))
          )(pg =>
            addMultipleParams(
              List(tcsEpics.guideModeCmd.setParam1(BinaryOnOff.On),
                   tcsEpics.guideModeCmd.setParam2(pg.from),
                   tcsEpics.guideModeCmd.setParam3(pg.to)
              )
            )
          )
      }
  }

  class TcsEpicsSystemImpl[F[_]: Monad: Parallel](epics: TcsEpics[F], st: TcsStatus[F])
      extends TcsEpicsSystem[F] {
    override def startCommand(timeout: FiniteDuration): TcsCommands[F] =
      TcsCommandsImpl(epics, timeout, List.empty)

    override val status: TcsStatus[F] = st
  }

  class TcsEpicsImpl[F[_]: Monad](
    applyCmd: GeminiApplyCommand[F],
    channels: TcsChannels[F]
  ) extends TcsEpics[F] {
    given Encoder[GuideProbe, String] = _ match {
      case GuideProbe.GmosOiwfs => "OIWFS"
      case GuideProbe.Pwfs1     => "PWFS1"
      case GuideProbe.Pwfs2     => "PWFS2"
    }

    override def post(timeout: FiniteDuration): VerifiedEpics[F, F, ApplyCommandResult] =
      applyCmd.post(timeout)

    override val mountParkCmd: ParameterlessCommandChannels[F] =
      ParameterlessCommandChannels(channels.telltale, channels.telescopeParkDir)

    override val mountFollowCmd: Command1Channels[F, BinaryOnOff] =
      Command1Channels(channels.telltale, channels.mountFollow)

    override val rotStopCmd: Command1Channels[F, BinaryYesNo] =
      Command1Channels(channels.telltale, channels.rotStopBrake)

    override val rotParkCmd: ParameterlessCommandChannels[F] =
      ParameterlessCommandChannels(channels.telltale, channels.rotParkDir)

    override val rotFollowCmd: Command1Channels[F, BinaryOnOff] =
      Command1Channels(channels.telltale, channels.rotFollow)

    override val rotMoveCmd: Command1Channels[F, Double] =
      Command1Channels(channels.telltale, channels.rotMoveAngle)

    override val carouselModeCmd
      : Command5Channels[F, String, String, Double, BinaryOnOff, BinaryOnOff] =
      Command5Channels(
        channels.telltale,
        channels.enclosure.ecsDomeMode,
        channels.enclosure.ecsShutterMode,
        channels.enclosure.ecsSlitHeight,
        channels.enclosure.ecsDomeEnable,
        channels.enclosure.ecsShutterEnable
      )

    override val carouselMoveCmd: Command1Channels[F, Double] =
      Command1Channels(channels.telltale, channels.enclosure.ecsMoveAngle)

    override val shuttersMoveCmd: Command2Channels[F, Double, Double] =
      Command2Channels(channels.telltale,
                       channels.enclosure.ecsShutterTop,
                       channels.enclosure.ecsShutterBottom
      )

    override val ventGatesMoveCmd: Command2Channels[F, Double, Double] =
      Command2Channels(channels.telltale,
                       channels.enclosure.ecsVentGateEast,
                       channels.enclosure.ecsVentGateWest
      )

    override val sourceACmd: TargetCommandChannels[F] =
      TargetCommandChannels[F](channels.telltale, channels.sourceA)

    override val oiwfsTargetCmd: TargetCommandChannels[F] =
      TargetCommandChannels[F](channels.telltale, channels.oiwfsTarget)

    override val wavelSourceA: Command1Channels[F, Double] =
      Command1Channels(channels.telltale, channels.wavelSourceA)

    override val slewCmd: SlewCommandChannels[F] =
      SlewCommandChannels(channels.telltale, channels.slew)

    override val rotatorConfigCmd: Command4Channels[F, Double, String, String, Double] =
      Command4Channels(
        channels.telltale,
        channels.rotator.ipa,
        channels.rotator.system,
        channels.rotator.equinox,
        channels.rotator.iaa
      )

    override val originCmd: Command6Channels[F, Double, Double, Double, Double, Double, Double] =
      Command6Channels(
        channels.telltale,
        channels.origin.xa,
        channels.origin.ya,
        channels.origin.xb,
        channels.origin.yb,
        channels.origin.xc,
        channels.origin.yc
      )

    override val focusOffsetCmd: Command1Channels[F, Double] = Command1Channels(
      channels.telltale,
      channels.focusOffset
    )

    override val oiwfsProbeTrackingCmd: ProbeTrackingCommandChannels[F] =
      ProbeTrackingCommandChannels(
        channels.telltale,
        channels.oiProbeTracking
      )
    override val oiwfsProbeCmds: ProbeCommandsChannels[F]               =
      buildProbeCommandsChannels(channels.telltale, channels.oiProbe)

    override val m1GuideConfigCmd: Command4Channels[F, String, String, Int, String] =
      Command4Channels(
        channels.telltale,
        channels.m1GuideConfig.weighting,
        channels.m1GuideConfig.source,
        channels.m1GuideConfig.frames,
        channels.m1GuideConfig.filename
      )

    override val m1GuideCmd: Command1Channels[F, BinaryOnOff] =
      Command1Channels(channels.telltale, channels.m1Guide)

    override val m2GuideCmd: Command1Channels[F, BinaryOnOff] =
      Command1Channels(channels.telltale, channels.m2Guide)

    override val m2GuideModeCmd: Command1Channels[F, BinaryOnOff] =
      Command1Channels(channels.telltale, channels.m2GuideMode)

    override val m2GuideConfigCmd
      : Command7Channels[F, String, Double, String, Double, Double, String, BinaryOnOff] =
      Command7Channels(
        channels.telltale,
        channels.m2GuideConfig.source,
        channels.m2GuideConfig.samplefreq,
        channels.m2GuideConfig.filter,
        channels.m2GuideConfig.freq1,
        channels.m2GuideConfig.freq2,
        channels.m2GuideConfig.beam,
        channels.m2GuideConfig.reset
      )

    override val m2GuideResetCmd: ParameterlessCommandChannels[F] = ParameterlessCommandChannels(
      channels.telltale,
      channels.m2GuideReset
    )

    override val mountGuideCmd: Command4Channels[F, BinaryOnOff, String, Double, Double] =
      new Command4Channels(
        channels.telltale,
        channels.mountGuide.mode,
        channels.mountGuide.source,
        channels.mountGuide.p1weight,
        channels.mountGuide.p2weight
      )
    override val oiWfsCmds: WfsCommandsChannels[F]                                       =
      WfsCommandsChannels.build(channels.telltale, channels.oiwfs)

    override val p1GuiderGainsCmd: Command3Channels[F, Double, Double, Double] =
      Command3Channels(
        channels.pwfs1Telltale,
        channels.guiderGains.p1TipGain,
        channels.guiderGains.p1TiltGain,
        channels.guiderGains.p1FocusGain
      )

    override val p2GuiderGainsCmd: Command3Channels[F, Double, Double, Double] =
      Command3Channels(
        channels.pwfs2Telltale,
        channels.guiderGains.p2TipGain,
        channels.guiderGains.p2TiltGain,
        channels.guiderGains.p2FocusGain
      )

    override val oiGuiderGainsCmd: Command3Channels[F, Double, Double, Double] =
      Command3Channels(
        channels.oiwfsTelltale,
        channels.guiderGains.oiTipGain,
        channels.guiderGains.oiTiltGain,
        channels.guiderGains.oiFocusGain
      )

    override val guideModeCmd: Command3Channels[F, BinaryOnOff, GuideProbe, GuideProbe] =
      Command3Channels(
        channels.telltale,
        channels.guideMode.state,
        channels.guideMode.from,
        channels.guideMode.to
      )
  }

  case class ParameterlessCommandChannels[F[_]: Monad](
    tt:         TelltaleChannel[F],
    dirChannel: Channel[F, CadDirective]
  ) {
    val mark: VerifiedEpics[F, F, Unit] =
      writeChannel[F, CadDirective](tt, dirChannel)(Applicative[F].pure(CadDirective.MARK))
  }

  case class Command1Channels[F[_]: Monad, A: Encoder[*, String]](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, String]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, A](tt, param1Channel)(v)
  }

  case class Command2Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String]](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, String],
    param2Channel: Channel[F, String]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, A](tt, param1Channel)(v)
    def setParam2(v: B): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, B](tt, param2Channel)(v)
  }

  case class Command3Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String], C: Encoder[
    *,
    String
  ]](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, String],
    param2Channel: Channel[F, String],
    param3Channel: Channel[F, String]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, A](tt, param1Channel)(v)
    def setParam2(v: B): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, B](tt, param2Channel)(v)
    def setParam3(v: C): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, C](tt, param3Channel)(v)
  }

  case class Command4Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String], C: Encoder[
    *,
    String
  ], D: Encoder[*, String]](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, String],
    param2Channel: Channel[F, String],
    param3Channel: Channel[F, String],
    param4Channel: Channel[F, String]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, A](tt, param1Channel)(v)
    def setParam2(v: B): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, B](tt, param2Channel)(v)
    def setParam3(v: C): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, C](tt, param3Channel)(v)
    def setParam4(v: D): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, D](tt, param4Channel)(v)
  }

  case class Command5Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String], C: Encoder[
    *,
    String
  ], D: Encoder[*, String], E: Encoder[*, String]](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, String],
    param2Channel: Channel[F, String],
    param3Channel: Channel[F, String],
    param4Channel: Channel[F, String],
    param5Channel: Channel[F, String]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, A](tt, param1Channel)(v)
    def setParam2(v: B): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, B](tt, param2Channel)(v)
    def setParam3(v: C): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, C](tt, param3Channel)(v)
    def setParam4(v: D): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, D](tt, param4Channel)(v)
    def setParam5(v: E): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, E](tt, param5Channel)(v)
  }

  case class Command6Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String], C: Encoder[
    *,
    String
  ], D: Encoder[*, String], E: Encoder[*, String], G: Encoder[*, String]](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, String],
    param2Channel: Channel[F, String],
    param3Channel: Channel[F, String],
    param4Channel: Channel[F, String],
    param5Channel: Channel[F, String],
    param6Channel: Channel[F, String]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, A](tt, param1Channel)(v)
    def setParam2(v: B): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, B](tt, param2Channel)(v)
    def setParam3(v: C): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, C](tt, param3Channel)(v)
    def setParam4(v: D): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, D](tt, param4Channel)(v)
    def setParam5(v: E): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, E](tt, param5Channel)(v)
    def setParam6(v: G): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, G](tt, param6Channel)(v)
  }

  case class Command7Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String], C: Encoder[
    *,
    String
  ], D: Encoder[*, String], E: Encoder[*, String], G: Encoder[*, String], H: Encoder[*, String]](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, String],
    param2Channel: Channel[F, String],
    param3Channel: Channel[F, String],
    param4Channel: Channel[F, String],
    param5Channel: Channel[F, String],
    param6Channel: Channel[F, String],
    param7Channel: Channel[F, String]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, A](tt, param1Channel)(v)

    def setParam2(v: B): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, B](tt, param2Channel)(v)

    def setParam3(v: C): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, C](tt, param3Channel)(v)

    def setParam4(v: D): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, D](tt, param4Channel)(v)

    def setParam5(v: E): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, E](tt, param5Channel)(v)

    def setParam6(v: G): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, G](tt, param6Channel)(v)

    def setParam7(v: H): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, H](tt, param7Channel)(v)
  }

  case class TargetCommandChannels[F[_]: Monad](
    tt:             TelltaleChannel[F],
    targetChannels: TargetChannels[F]
  ) {
    def objectName(v: String): VerifiedEpics[F, F, Unit]     =
      writeCadParam[F, String](tt, targetChannels.objectName)(v)
    def coordSystem(v: String): VerifiedEpics[F, F, Unit]    =
      writeCadParam[F, String](tt, targetChannels.coordSystem)(v)
    def coord1(v: Double): VerifiedEpics[F, F, Unit]         =
      writeCadParam[F, Double](tt, targetChannels.coord1)(v)
    def coord2(v: Double): VerifiedEpics[F, F, Unit]         =
      writeCadParam[F, Double](tt, targetChannels.coord2)(v)
    def epoch(v: Double): VerifiedEpics[F, F, Unit]          =
      writeCadParam[F, Double](tt, targetChannels.epoch)(v)
    def equinox(v: String): VerifiedEpics[F, F, Unit]        =
      writeCadParam[F, String](tt, targetChannels.equinox)(v)
    def parallax(v: Double): VerifiedEpics[F, F, Unit]       =
      writeCadParam[F, Double](tt, targetChannels.parallax)(v)
    def properMotion1(v: Double): VerifiedEpics[F, F, Unit]  =
      writeCadParam[F, Double](tt, targetChannels.properMotion1)(v)
    def properMotion2(v: Double): VerifiedEpics[F, F, Unit]  =
      writeCadParam[F, Double](tt, targetChannels.properMotion2)(v)
    def radialVelocity(v: Double): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, Double](tt, targetChannels.radialVelocity)(v)
    def brightness(v: Double): VerifiedEpics[F, F, Unit]     =
      writeCadParam[F, Double](tt, targetChannels.brightness)(v)
    def ephemerisFile(v: String): VerifiedEpics[F, F, Unit]  =
      writeCadParam[F, String](tt, targetChannels.ephemerisFile)(v)
  }

  case class SlewCommandChannels[F[_]: Monad](
    tt:           TelltaleChannel[F],
    slewChannels: SlewChannels[F]
  ) {
    def zeroChopThrow(v: BinaryOnOff): VerifiedEpics[F, F, Unit]            =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.zeroChopThrow)(v)
    def zeroSourceOffset(v: BinaryOnOff): VerifiedEpics[F, F, Unit]         =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.zeroSourceOffset)(v)
    def zeroSourceDiffTrack(v: BinaryOnOff): VerifiedEpics[F, F, Unit]      =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.zeroSourceDiffTrack)(v)
    def zeroMountOffset(v: BinaryOnOff): VerifiedEpics[F, F, Unit]          =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.zeroMountOffset)(v)
    def zeroMountDiffTrack(v: BinaryOnOff): VerifiedEpics[F, F, Unit]       =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.zeroMountDiffTrack)(v)
    def shortcircuitTargetFilter(v: BinaryOnOff): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.shortcircuitTargetFilter)(v)
    def shortcircuitMountFilter(v: BinaryOnOff): VerifiedEpics[F, F, Unit]  =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.shortcircuitMountFilter)(v)
    def resetPointing(v: BinaryOnOff): VerifiedEpics[F, F, Unit]            =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.resetPointing)(v)
    def stopGuide(v: BinaryOnOff): VerifiedEpics[F, F, Unit]                =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.stopGuide)(v)
    def zeroGuideOffset(v: BinaryOnOff): VerifiedEpics[F, F, Unit]          =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.zeroGuideOffset)(v)
    def zeroInstrumentOffset(v: BinaryOnOff): VerifiedEpics[F, F, Unit]     =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.zeroInstrumentOffset)(v)
    def autoparkPwfs1(v: BinaryOnOff): VerifiedEpics[F, F, Unit]            =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.autoparkPwfs1)(v)
    def autoparkPwfs2(v: BinaryOnOff): VerifiedEpics[F, F, Unit]            =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.autoparkPwfs2)(v)
    def autoparkOiwfs(v: BinaryOnOff): VerifiedEpics[F, F, Unit]            =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.autoparkOiwfs)(v)
    def autoparkGems(v: BinaryOnOff): VerifiedEpics[F, F, Unit]             =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.autoparkGems)(v)
    def autoparkAowfs(v: BinaryOnOff): VerifiedEpics[F, F, Unit]            =
      writeCadParam[F, BinaryOnOff](tt, slewChannels.autoparkAowfs)(v)
  }

  case class ProbeTrackingCommandChannels[F[_]: Monad](
    tt:                 TelltaleChannel[F],
    probeGuideChannels: ProbeTrackingChannels[F]
  ) {
    def nodAchopA(v: BinaryOnOff): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, BinaryOnOff](tt, probeGuideChannels.nodachopa)(v)

    def nodAchopB(v: BinaryOnOff): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, BinaryOnOff](tt, probeGuideChannels.nodachopb)(v)

    def nodBchopA(v: BinaryOnOff): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, BinaryOnOff](tt, probeGuideChannels.nodbchopa)(v)

    def nodBchopB(v: BinaryOnOff): VerifiedEpics[F, F, Unit] =
      writeCadParam[F, BinaryOnOff](tt, probeGuideChannels.nodbchopb)(v)
  }

  case class ProbeCommandsChannels[F[_]: Monad](
    park:   ParameterlessCommandChannels[F],
    follow: Command1Channels[F, BinaryOnOff]
  )

  def buildProbeCommandsChannels[F[_]: Monad](
    tt:            TelltaleChannel[F],
    probeChannels: ProbeChannels[F]
  ): ProbeCommandsChannels[F] = ProbeCommandsChannels(
    ParameterlessCommandChannels(tt, probeChannels.parkDir),
    Command1Channels(tt, probeChannels.follow)
  )

  case class WfsCommandsChannels[F[_]: Monad](
    observe:    Command7Channels[F, Int, Double, String, String, String, String, String],
    stop:       ParameterlessCommandChannels[F],
    signalProc: Command1Channels[F, String],
    dark:       Command1Channels[F, String],
    closedLoop: Command4Channels[F, Double, Int, Int, Double]
  )

  object WfsCommandsChannels {
    def build[F[_]: Monad](
      tt:          TelltaleChannel[F],
      wfsChannels: WfsChannels[F]
    ): WfsCommandsChannels[F] = WfsCommandsChannels(
      Command7Channels(
        tt,
        wfsChannels.observe.numberOfExposures,
        wfsChannels.observe.interval,
        wfsChannels.observe.options,
        wfsChannels.observe.label,
        wfsChannels.observe.output,
        wfsChannels.observe.path,
        wfsChannels.observe.fileName
      ),
      ParameterlessCommandChannels(tt, wfsChannels.stop),
      Command1Channels(tt, wfsChannels.procParams),
      Command1Channels(tt, wfsChannels.dark),
      Command4Channels(
        tt,
        wfsChannels.closedLoop.global,
        wfsChannels.closedLoop.average,
        wfsChannels.closedLoop.zernikes2m2,
        wfsChannels.closedLoop.mult
      )
    )
  }

  trait BaseCommand[F[_], +S] {
    def mark: S
  }

  trait FollowCommand[F[_], +S] {
    def setFollow(enable: Boolean): S
  }

  trait RotStopCommand[F[_], +S] {
    def setBrakes(enable: Boolean): S
  }

  trait RotMoveCommand[F[_], +S] {
    def setAngle(angle: Angle): S
  }

  trait CarouselModeCommand[F[_], +S] {
    def setDomeMode(mode:        DomeMode): S
    def setShutterMode(mode:     ShutterMode): S
    def setSlitHeight(height:    Double): S
    def setDomeEnable(enable:    Boolean): S
    def setShutterEnable(enable: Boolean): S
  }

  trait CarouselMoveCommand[F[_], +S] {
    def setAngle(angle: Angle): S
  }

  trait ShuttersMoveCommand[F[_], +S] {
    def setTop(pos:    Double): S
    def setBottom(pos: Double): S
  }

  trait VentGatesMoveCommand[F[_], +S] {
    def setVentGateEast(pos: Double): S
    def setVentGateWest(pos: Double): S
  }

  trait TargetCommand[F[_], +S] {
    def objectName(v:     String): S
    def coordSystem(v:    String): S
    def coord1(v:         Double): S
    def coord2(v:         Double): S
    def epoch(v:          Double): S
    def equinox(v:        String): S
    def parallax(v:       Double): S
    def properMotion1(v:  Double): S
    def properMotion2(v:  Double): S
    def radialVelocity(v: Double): S
    def brightness(v:     Double): S
    def ephemerisFile(v:  String): S
  }

  trait WavelengthCommand[F[_], +S] {
    def wavelength(v: Double): S
  }

  trait SlewOptionsCommand[F[_], +S] {
    def zeroChopThrow(v:            Boolean): S
    def zeroSourceOffset(v:         Boolean): S
    def zeroSourceDiffTrack(v:      Boolean): S
    def zeroMountOffset(v:          Boolean): S
    def zeroMountDiffTrack(v:       Boolean): S
    def shortcircuitTargetFilter(v: Boolean): S
    def shortcircuitMountFilter(v:  Boolean): S
    def resetPointing(v:            Boolean): S
    def stopGuide(v:                Boolean): S
    def zeroGuideOffset(v:          Boolean): S
    def zeroInstrumentOffset(v:     Boolean): S
    def autoparkPwfs1(v:            Boolean): S
    def autoparkPwfs2(v:            Boolean): S
    def autoparkOiwfs(v:            Boolean): S
    def autoparkGems(v:             Boolean): S
    def autoparkAowfs(v:            Boolean): S
  }

  trait RotatorCommand[F[_], +S] {
    def ipa(v:     Angle): S
    def system(v:  String): S
    def equinox(v: String): S
    def iaa(v:     Angle): S
  }

  trait OriginCommand[F[_], +S] {
    def originX(v: Distance): S
    def originY(v: Distance): S
  }

  trait FocusOffsetCommand[F[_], +S] {
    def focusOffset(v: Distance): S
  }

  trait ProbeTrackingCommand[F[_], +S] {
    def nodAchopA(v: Boolean): S
    def nodAchopB(v: Boolean): S
    def nodBchopA(v: Boolean): S
    def nodBchopB(v: Boolean): S
  }

  trait ProbeCommands[F[_], +S] {
    val park: BaseCommand[F, TcsCommands[F]]
    val follow: FollowCommand[F, TcsCommands[F]]
  }

  trait GuideCommand[F[_], +S] {
    def state(v: Boolean): S
  }

  trait GuiderGainsCommand[F[_], +S] {
    def dayTimeGains: S
    def defaultGains: S
  }

  trait M1GuideConfigCommand[F[_], +S] {
    def weighting(v: String): S
    def source(v:    String): S
    def frames(v:    Int): S
    def filename(v:  String): S
  }

  trait M2GuideModeCommand[F[_], +S] {
    def coma(v: Boolean): S
  }

  trait M2GuideConfigCommand[F[_], +S] {
    def source(v:     String): S
    def sampleFreq(v: Double): S
    def filter(v:     String): S
    def freq1(v:      Double): S
    def freq2(v:      Double): S
    def beam(v:       String): S
    def reset(v:      Boolean): S
  }

  trait MountGuideCommand[F[_], +S] {
    def mode(v:     Boolean): S
    def source(v:   String): S
    def p1Weight(v: Double): S
    def p2Weight(v: Double): S
  }

  trait WfsObserveCommand[F[_], +S] {
    def numberOfExposures(v: Int): S
    def interval(v:          Double): S
    def options(v:           String): S
    def label(v:             String): S
    def output(v:            String): S
    def path(v:              String): S
    def fileName(v:          String): S
  }

  trait GuideModeCommand[F[_], +S] {
    def setMode(pg: Option[ProbeGuide]): S
  }

  trait WfsSignalProcConfigCommand[F[_], +S] {
    def darkFilename(v: String): S
  }

  trait WfsDarkCommand[F[_], +S] {
    def filename(v: String): S
  }

  trait WfsClosedLoopCommand[F[_], +S] {
    def global(v:      Double): S
    def average(v:     Int): S
    def zernikes2m2(v: Int): S
    def mult(v:        Double): S
  }

  trait WfsCommands[F[_], +S] {
    val observe: WfsObserveCommand[F, S]
    val stop: BaseCommand[F, S]
    val signalProc: WfsSignalProcConfigCommand[F, S]
    val dark: WfsDarkCommand[F, S]
    val closedLoop: WfsClosedLoopCommand[F, S]
  }

  trait TcsCommands[F[_]] {
    def post: VerifiedEpics[F, F, ApplyCommandResult]
    val mcsParkCommand: BaseCommand[F, TcsCommands[F]]
    val mcsFollowCommand: FollowCommand[F, TcsCommands[F]]
    val rotStopCommand: RotStopCommand[F, TcsCommands[F]]
    val rotParkCommand: BaseCommand[F, TcsCommands[F]]
    val rotFollowCommand: FollowCommand[F, TcsCommands[F]]
    val rotMoveCommand: RotMoveCommand[F, TcsCommands[F]]
    val ecsCarouselModeCmd: CarouselModeCommand[F, TcsCommands[F]]
    val ecsCarouselMoveCmd: CarouselMoveCommand[F, TcsCommands[F]]
    val ecsShuttersMoveCmd: ShuttersMoveCommand[F, TcsCommands[F]]
    val ecsVenGatesMoveCmd: VentGatesMoveCommand[F, TcsCommands[F]]
    val sourceACmd: TargetCommand[F, TcsCommands[F]]
    val oiwfsTargetCmd: TargetCommand[F, TcsCommands[F]]
    val sourceAWavel: WavelengthCommand[F, TcsCommands[F]]
    val slewOptionsCommand: SlewOptionsCommand[F, TcsCommands[F]]
    val rotatorCommand: RotatorCommand[F, TcsCommands[F]]
    val originCommand: OriginCommand[F, TcsCommands[F]]
    val focusOffsetCommand: FocusOffsetCommand[F, TcsCommands[F]]
    val oiwfsProbeTrackingCommand: ProbeTrackingCommand[F, TcsCommands[F]]
    val oiwfsProbeCommands: ProbeCommands[F, TcsCommands[F]]
    val m1GuideCommand: GuideCommand[F, TcsCommands[F]]
    val m1GuideConfigCommand: M1GuideConfigCommand[F, TcsCommands[F]]
    val m2GuideCommand: GuideCommand[F, TcsCommands[F]]
    val m2GuideModeCommand: M2GuideModeCommand[F, TcsCommands[F]]
    val m2GuideConfigCommand: M2GuideConfigCommand[F, TcsCommands[F]]
    val m2GuideResetCommand: BaseCommand[F, TcsCommands[F]]
    val mountGuideCommand: MountGuideCommand[F, TcsCommands[F]]
    val oiWfsCommands: WfsCommands[F, TcsCommands[F]]
    val guiderGainsCommands: GuiderGainsCommand[F, TcsCommands[F]]
    val guideModeCommand: GuideModeCommand[F, TcsCommands[F]]
  }
  /*

  sealed trait VirtualGemsTelescope extends Product with Serializable

  object VirtualGemsTelescope {
    case object G1 extends VirtualGemsTelescope
    case object G2 extends VirtualGemsTelescope
    case object G3 extends VirtualGemsTelescope
    case object G4 extends VirtualGemsTelescope
  }

  trait OffsetCmd[F[_]] {
    def setX(v: Double): VerifiedEpics[F, F, Unit]
    def setY(v: Double): VerifiedEpics[F, F, Unit]
  }

  trait M2Beam[F[_]] {
    def setBeam(v: String): VerifiedEpics[F, F, Unit]
  }

  trait HrwfsPosCmd[F[_]] {
    def setHrwfsPos(v: String): VerifiedEpics[F, F, Unit]
  }

  trait ScienceFoldPosCmd[F[_]] {
    def setScfold(v: String): VerifiedEpics[F, F, Unit]
  }

  trait AoCorrect[F[_]] {
    def setCorrections(v: String): VerifiedEpics[F, F, Unit]
    def setGains(v:       Int): VerifiedEpics[F, F, Unit]
    def setMatrix(v:      Int): VerifiedEpics[F, F, Unit]
  }

  trait AoPrepareControlMatrix[F[_]] {
    def setX(v:             Double): VerifiedEpics[F, F, Unit]
    def setY(v:             Double): VerifiedEpics[F, F, Unit]
    def setSeeing(v:        Double): VerifiedEpics[F, F, Unit]
    def setStarMagnitude(v: Double): VerifiedEpics[F, F, Unit]
    def setWindSpeed(v:     Double): VerifiedEpics[F, F, Unit]
  }

  trait AoStatistics[F[_]] {
    def setFileName(v:            String): VerifiedEpics[F, F, Unit]
    def setSamples(v:             Int): VerifiedEpics[F, F, Unit]
    def setInterval(v:            Double): VerifiedEpics[F, F, Unit]
    def setTriggerTimeInterval(v: Double): VerifiedEpics[F, F, Unit]
  }

  trait TargetFilter[F[_]] {
    def setBandwidth(v:    Double): VerifiedEpics[F, F, Unit]
    def setMaxVelocity(v:  Double): VerifiedEpics[F, F, Unit]
    def setGrabRadius(v:   Double): VerifiedEpics[F, F, Unit]
    def setShortCircuit(v: String): VerifiedEpics[F, F, Unit]
  }
   */
}
