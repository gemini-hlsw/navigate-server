// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.{Applicative, Monad, Parallel}
import cats.effect.std.Dispatcher
import cats.effect.{Resource, Temporal}
import mouse.all._
import engage.epics.EpicsSystem.TelltaleChannel
import engage.epics._
import engage.epics.VerifiedEpics._
import engage.model.enums.{DomeMode, ShutterMode}
import engage.server.{ApplyCommandResult, tcs}
import engage.server.acm.ParameterList._
import engage.server.acm.{CadDirective, GeminiApplyCommand}
import engage.server.epicsdata.{BinaryOnOff, BinaryYesNo, DirSuffix}
import squants.Angle

import scala.concurrent.duration.FiniteDuration

trait TcsEpicsSystem[F[_]] {
  // TcsCommands accumulates the list of channels that need to be written to set parameters.
  // Once all the parameters are defined, the user calls the post method. Only then the EPICS channels will be verified,
  // the parameters written, and the apply record triggered.
  def startCommand(timeout: FiniteDuration): tcs.TcsEpicsSystem.TcsCommands[F]
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

    val carouselModeCmd: Command5Channels[F,
                                          DomeMode,
                                          ShutterMode,
                                          Double,
                                          BinaryOnOff,
                                          BinaryOnOff
    ]

    val carouselMoveCmd: Command1Channels[F, Double]

    val shuttersMoveCmd: Command2Channels[F, Double, Double]

    val ventGatesMoveCmd: Command2Channels[F, Double, Double]

    val sourceACmd: TargetCommandChannels[F]

    /*  val m1GuideCmd: M1GuideCmd[F]

  val m2GuideCmd: M2GuideCmd[F]

  val m2GuideModeCmd: M2GuideModeCmd[F]

  val m2GuideConfigCmd: M2GuideConfigCmd[F]

  val mountGuideCmd: MountGuideCmd[F]

  val offsetACmd: OffsetCmd[F]

  val offsetBCmd: OffsetCmd[F]

  val wavelSourceA: TargetWavelengthCmd[F]

  val wavelSourceB: TargetWavelengthCmd[F]

  val m2Beam: M2Beam[F]

  val pwfs1ProbeGuideCmd: ProbeGuideCmd[F]

  val pwfs2ProbeGuideCmd: ProbeGuideCmd[F]

  val oiwfsProbeGuideCmd: ProbeGuideCmd[F]

  val pwfs1ProbeFollowCmd: ProbeFollowCmd[F]

  val pwfs2ProbeFollowCmd: ProbeFollowCmd[F]

  val oiwfsProbeFollowCmd: ProbeFollowCmd[F]

  val aoProbeFollowCmd: ProbeFollowCmd[F]

  val pwfs1Park: EpicsCommand[F]

  val pwfs2Park: EpicsCommand[F]

  val oiwfsPark: EpicsCommand[F]

  val pwfs1StopObserveCmd: EpicsCommand[F]

  val pwfs2StopObserveCmd: EpicsCommand[F]

  val oiwfsStopObserveCmd: EpicsCommand[F]

  val pwfs1ObserveCmd: WfsObserveCmd[F]

  val pwfs2ObserveCmd: WfsObserveCmd[F]

  val oiwfsObserveCmd: WfsObserveCmd[F]

  val hrwfsParkCmd: EpicsCommand[F]

  val hrwfsPosCmd: HrwfsPosCmd[F]

  val scienceFoldParkCmd: EpicsCommand[F]

  val scienceFoldPosCmd: ScienceFoldPosCmd[F]

  val observe: EpicsCommand[F]

  val endObserve: EpicsCommand[F]

  val aoCorrect: AoCorrect[F]

  val aoPrepareControlMatrix: AoPrepareControlMatrix[F]

  val aoFlatten: EpicsCommand[F]

  val aoStatistics: AoStatistics[F]

  val targetFilter: TargetFilter[F]

  def absorbTipTilt: F[Int]

  def m1GuideSource: F[String]

  def m1Guide: F[BinaryOnOff]

  def m2p1Guide: F[String]

  def m2p2Guide: F[String]

  def m2oiGuide: F[String]

  def m2aoGuide: F[String]

  def comaCorrect: F[String]

  def m2GuideState: F[BinaryOnOff]

  def xoffsetPoA1: F[Double]

  def yoffsetPoA1: F[Double]

  def xoffsetPoB1: F[Double]

  def yoffsetPoB1: F[Double]

  def xoffsetPoC1: F[Double]

  def yoffsetPoC1: F[Double]

  def sourceAWavelength: F[Double]

  def sourceBWavelength: F[Double]

  def sourceCWavelength: F[Double]

  def chopBeam: F[String]

  def p1FollowS: F[String]

  def p2FollowS: F[String]

  def oiFollowS: F[String]

  def aoFollowS: F[String]

  def p1Parked: F[Boolean]

  def p2Parked: F[Boolean]

  def oiName: F[String]

  def oiParked: F[Boolean]

  def pwfs1On: F[BinaryYesNo]

  def pwfs2On: F[BinaryYesNo]

  def oiwfsOn: F[BinaryYesNo]

  def sfName: F[String]

  def sfParked: F[Int]

  def agHwName: F[String]

  def agHwParked: F[Int]

  def instrAA: F[Double]

  def inPosition: F[String]

  def agInPosition: F[Double]

  val pwfs1ProbeGuideConfig: ProbeGuideConfig[F]

  val pwfs2ProbeGuideConfig: ProbeGuideConfig[F]

  val oiwfsProbeGuideConfig: ProbeGuideConfig[F]

  // This functions returns a F that, when run, first waits tcsSettleTime to absorb in-position transients, then waits
  // for the in-position to change to true and stay true for stabilizationTime. It will wait up to `timeout`
  // seconds for that to happen.
  def waitInPosition(stabilizationTime: Duration, timeout: FiniteDuration)(implicit
    T:                                  Timer[F]
  ): F[Unit]

  // `waitAGInPosition` works like `waitInPosition`, but for the AG in-position flag.
  /* TODO: AG inposition can take up to 1[s] to react to a TCS command. If the value is read before that, it may induce
     * an error. A better solution is to detect the edge, from not in position to in-position.
     */
  def waitAGInPosition(timeout: FiniteDuration)(implicit T: Timer[F]): F[Unit]

  def hourAngle: F[String]

  def localTime: F[String]

  def trackingFrame: F[String]

  def trackingEpoch: F[Double]

  def equinox: F[Double]

  def trackingEquinox: F[String]

  def trackingDec: F[Double]

  def trackingRA: F[Double]

  def elevation: F[Double]

  def azimuth: F[Double]

  def crPositionAngle: F[Double]

  def ut: F[String]

  def date: F[String]

  def m2Baffle: F[String]

  def m2CentralBaffle: F[String]

  def st: F[String]

  def sfRotation: F[Double]

  def sfTilt: F[Double]

  def sfLinear: F[Double]

  def instrPA: F[Double]

  def targetA: F[List[Double]]

  def aoFoldPosition: F[String]

  def useAo: F[BinaryYesNo]

  def airmass: F[Double]

  def airmassStart: F[Double]

  def airmassEnd: F[Double]

  def carouselMode: F[String]

  def crFollow: F[Int]

  def crTrackingFrame: F[String]

  def sourceATarget: Target[F]

  val pwfs1Target: Target[F]

  val pwfs2Target: Target[F]

  val oiwfsTarget: Target[F]

  def parallacticAngle: F[Angle]

  def m2UserFocusOffset: F[Double]

  def pwfs1IntegrationTime: F[Double]

  def pwfs2IntegrationTime: F[Double]

  // Attribute must be changed back to Double after EPICS channel is fixed.
  def oiwfsIntegrationTime: F[Double]

  def gsaoiPort: F[Int]

  def gpiPort: F[Int]

  def f2Port: F[Int]

  def niriPort: F[Int]

  def gnirsPort: F[Int]

  def nifsPort: F[Int]

  def gmosPort: F[Int]

  def ghostPort: F[Int]

  def aoGuideStarX: F[Double]

  def aoGuideStarY: F[Double]

  def aoPreparedCMX: F[Double]

  def aoPreparedCMY: F[Double]

  // GeMS Commands
  import VirtualGemsTelescope._

  val g1ProbeGuideCmd: ProbeGuideCmd[F]

  val g2ProbeGuideCmd: ProbeGuideCmd[F]

  val g3ProbeGuideCmd: ProbeGuideCmd[F]

  val g4ProbeGuideCmd: ProbeGuideCmd[F]

  def gemsProbeGuideCmd(g: VirtualGemsTelescope): ProbeGuideCmd[F] = g match {
    case G1 => g1ProbeGuideCmd
    case G2 => g2ProbeGuideCmd
    case G3 => g3ProbeGuideCmd
    case G4 => g4ProbeGuideCmd
  }

  val wavelG1: TargetWavelengthCmd[F]

  val wavelG2: TargetWavelengthCmd[F]

  val wavelG3: TargetWavelengthCmd[F]

  val wavelG4: TargetWavelengthCmd[F]

  def gemsWavelengthCmd(g: VirtualGemsTelescope): TargetWavelengthCmd[F] = g match {
    case G1 => wavelG1
    case G2 => wavelG2
    case G3 => wavelG3
    case G4 => wavelG4
  }

  def gwfs1Target: Target[F]

  def gwfs2Target: Target[F]

  def gwfs3Target: Target[F]

  def gwfs4Target: Target[F]

  def gemsTarget(g: VirtualGemsTelescope): Target[F] = g match {
    case G1 => gwfs1Target
    case G2 => gwfs2Target
    case G3 => gwfs3Target
    case G4 => gwfs4Target
  }

  val cwfs1ProbeFollowCmd: ProbeFollowCmd[F]

  val cwfs2ProbeFollowCmd: ProbeFollowCmd[F]

  val cwfs3ProbeFollowCmd: ProbeFollowCmd[F]

  val odgw1FollowCmd: ProbeFollowCmd[F]

  val odgw2FollowCmd: ProbeFollowCmd[F]

  val odgw3FollowCmd: ProbeFollowCmd[F]

  val odgw4FollowCmd: ProbeFollowCmd[F]

  val odgw1ParkCmd: EpicsCommand[F]

  val odgw2ParkCmd: EpicsCommand[F]

  val odgw3ParkCmd: EpicsCommand[F]

  val odgw4ParkCmd: EpicsCommand[F]

  // GeMS statuses

  def cwfs1Follow: F[Boolean]

  def cwfs2Follow: F[Boolean]

  def cwfs3Follow: F[Boolean]

  def odgw1Follow: F[Boolean]

  def odgw2Follow: F[Boolean]

  def odgw3Follow: F[Boolean]

  def odgw4Follow: F[Boolean]

  def odgw1Parked: F[Boolean]

  def odgw2Parked: F[Boolean]

  def odgw3Parked: F[Boolean]

  def odgw4Parked: F[Boolean]

  def g1MapName: F[Option[GemsSource]]

  def g2MapName: F[Option[GemsSource]]

  def g3MapName: F[Option[GemsSource]]

  def g4MapName: F[Option[GemsSource]]

  def g1Wavelength: F[Double]

  def g2Wavelength: F[Double]

  def g3Wavelength: F[Double]

  def g4Wavelength: F[Double]

  def gemsWavelength(g: VirtualGemsTelescope): F[Double] = g match {
    case G1 => g1Wavelength
    case G2 => g2Wavelength
    case G3 => g3Wavelength
    case G4 => g4Wavelength
  }

  val g1GuideConfig: ProbeGuideConfig[F]

  val g2GuideConfig: ProbeGuideConfig[F]

  val g3GuideConfig: ProbeGuideConfig[F]

  val g4GuideConfig: ProbeGuideConfig[F]

  def gemsGuideConfig(g: VirtualGemsTelescope): ProbeGuideConfig[F] = g match {
    case G1 => g1GuideConfig
    case G2 => g2GuideConfig
    case G3 => g3GuideConfig
    case G4 => g4GuideConfig
  }
     */
  }

  val sysName: String = "TCS"

  val className: String = getClass.getName

  private[tcs] def buildSystem[F[_]: Monad: Parallel](
    applyCmd: GeminiApplyCommand[F],
    channels: TcsChannels[F]
  ): TcsEpicsSystem[F] = new TcsEpicsSystemImpl[F](new TcsEpicsImpl[F](applyCmd, channels))

  def build[F[_]: Dispatcher: Temporal: Parallel](
    service: EpicsService[F],
    tops:    Map[String, String]
  ): Resource[F, TcsEpicsSystem[F]] = {
    val top = tops.getOrElse("tcs", "tcs:")
    for {
      channels <- buildChannels(service, top)
      applyCmd <-
        GeminiApplyCommand.build(service, channels.telltale, top + "apply", top + "applyC")
    } yield buildSystem(applyCmd, channels)
  }

  case class TcsChannels[F[_]](
    telltale:         TelltaleChannel[F],
    telescopeParkDir: Channel[F, CadDirective],
    mountFollow:      Channel[F, BinaryOnOff],
    rotStopBrake:     Channel[F, BinaryYesNo],
    rotParkDir:       Channel[F, CadDirective],
    rotFollow:        Channel[F, BinaryOnOff],
    rotMoveAngle:     Channel[F, Double],
    enclosure:        EnclosureChannels[F],
    sourceA:          TargetChannels[F]
  )

  case class EnclosureChannels[F[_]](
    ecsDomeMode:      Channel[F, DomeMode],
    ecsShutterMode:   Channel[F, ShutterMode],
    ecsSlitHeight:    Channel[F, Double],
    ecsDomeEnable:    Channel[F, BinaryOnOff],
    ecsShutterEnable: Channel[F, BinaryOnOff],
    ecsMoveAngle:     Channel[F, Double],
    ecsShutterTop:    Channel[F, Double],
    ecsShutterBottom: Channel[F, Double],
    ecsVentGateEast:  Channel[F, Double],
    ecsVentGateWest:  Channel[F, Double]
  )

  case class TargetChannels[F[_]](
    objectName:     Channel[F, String],
    coordSystem:    Channel[F, String],
    coord1:         Channel[F, Double],
    coord2:         Channel[F, Double],
    epoch:          Channel[F, String],
    equinox:        Channel[F, String],
    parallax:       Channel[F, Double],
    properMotion1:  Channel[F, Double],
    properMotion2:  Channel[F, Double],
    radialVelocity: Channel[F, Double],
    brightness:     Channel[F, Double],
    ephemerisFile:  Channel[F, String]
  )

  def buildEnclosureChannels[F[_]](
    service: EpicsService[F],
    top:     String
  ): Resource[F, EnclosureChannels[F]] =
    for {
      edm <- service.getChannel[DomeMode](top + "carouselMode.A")
      esm <- service.getChannel[ShutterMode](top + "carouselMode.B")
      esh <- service.getChannel[Double](top + "carouselMode.C")
      ede <- service.getChannel[BinaryOnOff](top + "carouselMode.D")
      ese <- service.getChannel[BinaryOnOff](top + "carouselMode.E")
      ema <- service.getChannel[Double](top + "carousel.A")
      est <- service.getChannel[Double](top + "shutter.A")
      esb <- service.getChannel[Double](top + "shutter.B")
      eve <- service.getChannel[Double](top + "ventgates.A")
      evw <- service.getChannel[Double](top + "ventgates.B")
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
      co1 <- service.getChannel[Double](prefix + ".C")
      co2 <- service.getChannel[Double](prefix + ".D")
      ep  <- service.getChannel[String](prefix + ".E")
      eq  <- service.getChannel[String](prefix + ".F")
      pr  <- service.getChannel[Double](prefix + ".G")
      pm1 <- service.getChannel[Double](prefix + ".H")
      pm2 <- service.getChannel[Double](prefix + ".I")
      rv  <- service.getChannel[Double](prefix + ".J")
      br  <- service.getChannel[Double](prefix + ".K")
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

  def buildChannels[F[_]](service: EpicsService[F], top: String): Resource[F, TcsChannels[F]] =
    for {
      tt  <- service.getChannel[String](top + "sad:health.VAL").map(TelltaleChannel(sysName, _))
      tpd <- service.getChannel[CadDirective](top + "telpark" + DirSuffix)
      mf  <- service.getChannel[BinaryOnOff](top + "mcFollow.A")
      rsb <- service.getChannel[BinaryYesNo](top + "rotStop.B")
      rpd <- service.getChannel[CadDirective](top + "rotPark" + DirSuffix)
      rf  <- service.getChannel[BinaryOnOff](top + "crFollow.A")
      rma <- service.getChannel[Double](top + "rotMove.A")
      ecs <- buildEnclosureChannels(service, top)
      sra <- buildTargetChannels(service, top + "sourceA")
    } yield TcsChannels[F](
      tt,
      tpd,
      mf,
      rsb,
      rpd,
      rf,
      rma,
      ecs,
      sra
    )

  case class TcsCommandsImpl[F[_]: Monad: Parallel](
    tcsEpics: TcsEpics[F],
    timeout:  FiniteDuration,
    params:   ParameterList[F]
  ) extends TcsCommands[F] {

    private def addParam(p: VerifiedEpics[F, F, Unit]): TcsCommands[F] =
      TcsCommandsImpl(tcsEpics, timeout, params :+ p)

    override def post: VerifiedEpics[F, F, ApplyCommandResult] =
      params.compile *> tcsEpics.post(timeout)

    override val mcsParkCommand: BaseCommand[F, TcsCommands[F]] =
      new BaseCommand[F, TcsCommands[F]] {
        override def mark: TcsCommands[F] = addParam(tcsEpics.mountParkCmd.mark)
      }

    override val mcsFollowCommand: FollowCommand[F, TcsCommands[F]] =
      new FollowCommand[F, TcsCommands[F]] {
        override def setFollow(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.mountFollowCmd.setParam1(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )
      }

    override val rotStopCommand: RotStopCommand[F, TcsCommands[F]] =
      new RotStopCommand[F, TcsCommands[F]] {
        override def setBrakes(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.rotStopCmd.setParam1(enable.fold(BinaryYesNo.Yes, BinaryYesNo.No))
        )
      }

    override val rotParkCommand: BaseCommand[F, TcsCommands[F]] =
      new BaseCommand[F, TcsCommands[F]] {
        override def mark: TcsCommands[F] = addParam(tcsEpics.rotParkCmd.mark)
      }

    override val rotFollowCommand: FollowCommand[F, TcsCommands[F]] =
      new FollowCommand[F, TcsCommands[F]] {
        override def setFollow(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.rotFollowCmd.setParam1(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )
      }

    override val rotMoveCommand: RotMoveCommand[F, TcsCommands[F]] =
      new RotMoveCommand[F, TcsCommands[F]] {
        override def setAngle(angle: Angle): TcsCommands[F] = addParam(
          tcsEpics.rotMoveCmd.setParam1(angle.toDegrees)
        )
      }

    override val ecsCarouselModeCmd: CarouselModeCommand[F, TcsCommands[F]] =
      new CarouselModeCommand[F, TcsCommands[F]] {
        override def setDomeMode(mode: DomeMode): TcsCommands[F] = addParam(
          tcsEpics.carouselModeCmd.setParam1(mode)
        )

        override def setShutterMode(mode: ShutterMode): TcsCommands[F] = addParam(
          tcsEpics.carouselModeCmd.setParam2(mode)
        )

        override def setSlitHeight(height: Double): TcsCommands[F] = addParam(
          tcsEpics.carouselModeCmd.setParam3(height)
        )

        override def setDomeEnable(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.carouselModeCmd.setParam4(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )

        override def setShutterEnable(enable: Boolean): TcsCommands[F] = addParam(
          tcsEpics.carouselModeCmd.setParam5(enable.fold(BinaryOnOff.On, BinaryOnOff.Off))
        )
      }

    override val ecsCarouselMoveCmd: CarouselMoveCommand[F, TcsCommands[F]] =
      new CarouselMoveCommand[F, TcsCommands[F]] {
        override def setAngle(angle: Angle): TcsCommands[F] = addParam(
          tcsEpics.carouselMoveCmd.setParam1(angle.toDegrees)
        )
      }

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

        override def epoch(v: String): TcsCommands[F] = addParam(
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
  }

  class TcsEpicsSystemImpl[F[_]: Monad: Parallel](epics: TcsEpics[F]) extends TcsEpicsSystem[F] {
    override def startCommand(timeout: FiniteDuration): TcsCommands[F] =
      TcsCommandsImpl(epics, timeout, List.empty)
  }

  class TcsEpicsImpl[F[_]: Monad](
    applyCmd: GeminiApplyCommand[F],
    channels: TcsChannels[F]
  ) extends TcsEpics[F] {
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
      : Command5Channels[F, DomeMode, ShutterMode, Double, BinaryOnOff, BinaryOnOff] =
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
  }

  case class ParameterlessCommandChannels[F[_]: Monad](
    tt:         TelltaleChannel[F],
    dirChannel: Channel[F, CadDirective]
  ) {
    val mark: VerifiedEpics[F, F, Unit] =
      writeChannel[F, CadDirective](tt, dirChannel)(Applicative[F].pure(CadDirective.MARK))
  }

  case class Command1Channels[F[_]: Monad, A](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, A]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeChannel[F, A](tt, param1Channel)(Applicative[F].pure(v))
  }

  case class Command2Channels[F[_]: Monad, A, B](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, A],
    param2Channel: Channel[F, B]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeChannel[F, A](tt, param1Channel)(Applicative[F].pure(v))
    def setParam2(v: B): VerifiedEpics[F, F, Unit] =
      writeChannel[F, B](tt, param2Channel)(Applicative[F].pure(v))
  }

  case class Command3Channels[F[_]: Monad, A, B, C](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, A],
    param2Channel: Channel[F, B],
    param3Channel: Channel[F, C]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeChannel[F, A](tt, param1Channel)(Applicative[F].pure(v))
    def setParam2(v: B): VerifiedEpics[F, F, Unit] =
      writeChannel[F, B](tt, param2Channel)(Applicative[F].pure(v))
    def setParam3(v: C): VerifiedEpics[F, F, Unit] =
      writeChannel[F, C](tt, param3Channel)(Applicative[F].pure(v))
  }

  case class Command4Channels[F[_]: Monad, A, B, C, D](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, A],
    param2Channel: Channel[F, B],
    param3Channel: Channel[F, C],
    param4Channel: Channel[F, D]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeChannel[F, A](tt, param1Channel)(Applicative[F].pure(v))
    def setParam2(v: B): VerifiedEpics[F, F, Unit] =
      writeChannel[F, B](tt, param2Channel)(Applicative[F].pure(v))
    def setParam3(v: C): VerifiedEpics[F, F, Unit] =
      writeChannel[F, C](tt, param3Channel)(Applicative[F].pure(v))
    def setParam4(v: D): VerifiedEpics[F, F, Unit] =
      writeChannel[F, D](tt, param4Channel)(Applicative[F].pure(v))
  }

  case class Command5Channels[F[_]: Monad, A, B, C, D, E](
    tt:            TelltaleChannel[F],
    param1Channel: Channel[F, A],
    param2Channel: Channel[F, B],
    param3Channel: Channel[F, C],
    param4Channel: Channel[F, D],
    param5Channel: Channel[F, E]
  ) {
    def setParam1(v: A): VerifiedEpics[F, F, Unit] =
      writeChannel[F, A](tt, param1Channel)(Applicative[F].pure(v))
    def setParam2(v: B): VerifiedEpics[F, F, Unit] =
      writeChannel[F, B](tt, param2Channel)(Applicative[F].pure(v))
    def setParam3(v: C): VerifiedEpics[F, F, Unit] =
      writeChannel[F, C](tt, param3Channel)(Applicative[F].pure(v))
    def setParam4(v: D): VerifiedEpics[F, F, Unit] =
      writeChannel[F, D](tt, param4Channel)(Applicative[F].pure(v))
    def setParam5(v: E): VerifiedEpics[F, F, Unit] =
      writeChannel[F, E](tt, param5Channel)(Applicative[F].pure(v))
  }

  case class TargetCommandChannels[F[_]: Monad](
    tt:             TelltaleChannel[F],
    targetChannels: TargetChannels[F]
  ) {
    def objectName(v: String): VerifiedEpics[F, F, Unit]     =
      writeChannel[F, String](tt, targetChannels.objectName)(Applicative[F].pure(v))
    def coordSystem(v: String): VerifiedEpics[F, F, Unit]    =
      writeChannel[F, String](tt, targetChannels.coordSystem)(Applicative[F].pure(v))
    def coord1(v: Double): VerifiedEpics[F, F, Unit]         =
      writeChannel[F, Double](tt, targetChannels.coord1)(Applicative[F].pure(v))
    def coord2(v: Double): VerifiedEpics[F, F, Unit]         =
      writeChannel[F, Double](tt, targetChannels.coord2)(Applicative[F].pure(v))
    def epoch(v: String): VerifiedEpics[F, F, Unit]          =
      writeChannel[F, String](tt, targetChannels.epoch)(Applicative[F].pure(v))
    def equinox(v: String): VerifiedEpics[F, F, Unit]        =
      writeChannel[F, String](tt, targetChannels.equinox)(Applicative[F].pure(v))
    def parallax(v: Double): VerifiedEpics[F, F, Unit]       =
      writeChannel[F, Double](tt, targetChannels.parallax)(Applicative[F].pure(v))
    def properMotion1(v: Double): VerifiedEpics[F, F, Unit]  =
      writeChannel[F, Double](tt, targetChannels.properMotion1)(Applicative[F].pure(v))
    def properMotion2(v: Double): VerifiedEpics[F, F, Unit]  =
      writeChannel[F, Double](tt, targetChannels.properMotion2)(Applicative[F].pure(v))
    def radialVelocity(v: Double): VerifiedEpics[F, F, Unit] =
      writeChannel[F, Double](tt, targetChannels.radialVelocity)(Applicative[F].pure(v))
    def brightness(v: Double): VerifiedEpics[F, F, Unit]     =
      writeChannel[F, Double](tt, targetChannels.brightness)(Applicative[F].pure(v))
    def ephemerisFile(v: String): VerifiedEpics[F, F, Unit]  =
      writeChannel[F, String](tt, targetChannels.ephemerisFile)(Applicative[F].pure(v))
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
    def epoch(v:          String): S
    def equinox(v:        String): S
    def parallax(v:       Double): S
    def properMotion1(v:  Double): S
    def properMotion2(v:  Double): S
    def radialVelocity(v: Double): S
    def brightness(v:     Double): S
    def ephemerisFile(v:  String): S
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

  }
  /*
  trait ProbeGuideCmd[F[_]] extends EpicsCommand[F] {
    def setNodachopa(v: String): F[Unit]
    def setNodachopb(v: String): F[Unit]
    def setNodbchopa(v: String): F[Unit]
    def setNodbchopb(v: String): F[Unit]
  }

  final class ProbeGuideCmdImpl[F[_]: Async](csName: String, epicsService: CaService)
      extends EpicsCommandBase[F](sysName)
      with ProbeGuideCmd[F] {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender(csName))

    private val nodachopa                         = cs.map(_.getString("nodachopa"))
    override def setNodachopa(v: String): F[Unit] = setParameter(nodachopa, v)

    private val nodachopb                         = cs.map(_.getString("nodachopb"))
    override def setNodachopb(v: String): F[Unit] = setParameter(nodachopb, v)

    private val nodbchopa                         = cs.map(_.getString("nodbchopa"))
    override def setNodbchopa(v: String): F[Unit] = setParameter(nodbchopa, v)

    private val nodbchopb                         = cs.map(_.getString("nodbchopb"))
    override def setNodbchopb(v: String): F[Unit] = setParameter(nodbchopb, v)
  }

  trait WfsObserveCmd[F[_]] extends EpicsCommand[F] {
    def setNoexp(v:  Integer): F[Unit]
    def setInt(v:    Double): F[Unit]
    def setOutopt(v: String): F[Unit]
    def setLabel(v:  String): F[Unit]
    def setOutput(v: String): F[Unit]
    def setPath(v:   String): F[Unit]
    def setName(v:   String): F[Unit]
  }

  final class WfsObserveCmdImpl[F[_]: Async](csName: String, epicsService: CaService)
      extends EpicsCommandBase[F](sysName)
      with WfsObserveCmd[F] {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender(csName))

    private val noexp                          = cs.map(_.getInteger("noexp"))
    override def setNoexp(v: Integer): F[Unit] = setParameter(noexp, v)

    private val int                         = cs.map(_.getDouble("int"))
    override def setInt(v: Double): F[Unit] = setParameter[F, java.lang.Double](int, v)

    private val outopt                         = cs.map(_.getString("outopt"))
    override def setOutopt(v: String): F[Unit] = setParameter(outopt, v)

    private val label                         = cs.map(_.getString("label"))
    override def setLabel(v: String): F[Unit] = setParameter(label, v)

    private val output                         = cs.map(_.getString("output"))
    override def setOutput(v: String): F[Unit] = setParameter(output, v)

    private val path                         = cs.map(_.getString("path"))
    override def setPath(v: String): F[Unit] = setParameter(path, v)

    private val name                         = cs.map(_.getString("name"))
    override def setName(v: String): F[Unit] = setParameter(name, v)
  }

  trait ProbeFollowCmd[F[_]] extends EpicsCommand[F] {
    def setFollowState(v: String): F[Unit]
  }

  final class ProbeFollowCmdImpl[F[_]: Async](csName: String, epicsService: CaService)
      extends EpicsCommandBase[F](sysName)
      with ProbeFollowCmd[F] {
    override protected val cs: Option[CaCommandSender] = Option(
      epicsService.getCommandSender(csName)
    )

    private val follow                              = cs.map(_.getString("followState"))
    override def setFollowState(v: String): F[Unit] = setParameter(follow, v)
  }

  trait TargetWavelengthCmd[F[_]] extends EpicsCommand[F] {
    def setWavel(v: Double): F[Unit]
  }

  final class TargetWavelengthCmdImpl[F[_]: Async](csName: String, epicsService: CaService)
      extends EpicsCommandBase[F](sysName)
      with TargetWavelengthCmd[F] {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender(csName))

    private val wavel = cs.map(_.getDouble("wavel"))

    override def setWavel(v: Double): F[Unit] = setParameter[F, java.lang.Double](wavel, v)
  }

  case class ProbeGuideChannels[F[_]](
    val nodachopa: Channel[F, Int],
    val nodachopb: Channel[F, Int],
    val nodbchopa: Channel[F, Int],
    val nodbchopb: Channel[F, Int]
  )

  trait ProbeGuideConfig[F[_]] {
    def nodachopa: VerifiedEpics[F, F, Int]
    def nodachopb: VerifiedEpics[F, F, Int]
    def nodbchopa: VerifiedEpics[F, F, Int]
    def nodbchopb: VerifiedEpics[F, F, Int]
  }

  final class ProbeGuideConfigImpl[F[_]: Sync](
    protected val prefix:   String,
    protected val service: EpicsService[F]
  ) extends ProbeGuideConfig[F] {
    private val aa = service.
    override def nodachopa: VerifiedEpics[F, F, Int] = service.safeAttributeSIntF(
      tcsState.getIntegerAttribute(prefix + "nodachopa")
    )
    override def nodachopb: VerifiedEpics[F, F, Int] = safeAttributeSIntF(
      tcsState.getIntegerAttribute(prefix + "nodachopb")
    )
    override def nodbchopa: VerifiedEpics[F, F, Int] = safeAttributeSIntF(
      tcsState.getIntegerAttribute(prefix + "nodbchopa")
    )
    override def nodbchopb: VerifiedEpics[F, F, Int] = safeAttributeSIntF(
      tcsState.getIntegerAttribute(prefix + "nodbchopb")
    )
  }

  trait Target[F[_]] {
    def objectName: F[String]
    def ra: F[Double]
    def dec: F[Double]
    def frame: F[String]
    def equinox: F[String]
    def epoch: F[String]
    def properMotionRA: F[Double]
    def properMotionDec: F[Double]
    def centralWavelenght: F[Double]
    def parallax: F[Double]
    def radialVelocity: F[Double]
  }

  implicit class TargetIOOps(val tio: Target[IO]) extends AnyVal {
    def to[F[_]: LiftIO]: Target[F] = new Target[F] {
      def objectName: F[String]        = tio.objectName.to[F]
      def ra: F[Double]                = tio.ra.to[F]
      def dec: F[Double]               = tio.dec.to[F]
      def frame: F[String]             = tio.frame.to[F]
      def equinox: F[String]           = tio.equinox.to[F]
      def epoch: F[String]             = tio.epoch.to[F]
      def properMotionRA: F[Double]    = tio.properMotionRA.to[F]
      def properMotionDec: F[Double]   = tio.properMotionDec.to[F]
      def centralWavelenght: F[Double] = tio.centralWavelenght.to[F]
      def parallax: F[Double]          = tio.parallax.to[F]
      def radialVelocity: F[Double]    = tio.radialVelocity.to[F]
    }
  }

  sealed trait VirtualGemsTelescope extends Product with Serializable

  object VirtualGemsTelescope {
    case object G1 extends VirtualGemsTelescope
    case object G2 extends VirtualGemsTelescope
    case object G3 extends VirtualGemsTelescope
    case object G4 extends VirtualGemsTelescope
  }

  trait M1GuideCmd[F[_]] {
    def setState(v: String): VerifiedEpics[F, F, Unit]
  }

  trait M2GuideCmd[F[_]] {
    def setState(v: String): VerifiedEpics[F, F, Unit]
  }

  trait M2GuideModeCmd[F[_]] {
    def setComa(v: String): VerifiedEpics[F, F, Unit]
  }

  trait M2GuideConfigCmd[F[_]] {
    def setSource(v: String): VerifiedEpics[F, F, Unit]
    def setBeam(v:   String): VerifiedEpics[F, F, Unit]
    def setReset(v:  String): VerifiedEpics[F, F, Unit]
  }

  trait MountGuideCmd[F[_]] {
    def setSource(v:   String): VerifiedEpics[F, F, Unit]
    def setP1Weight(v: Double): VerifiedEpics[F, F, Unit]
    def setP2Weight(v: Double): VerifiedEpics[F, F, Unit]
    def setMode(v:     String): VerifiedEpics[F, F, Unit]
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
