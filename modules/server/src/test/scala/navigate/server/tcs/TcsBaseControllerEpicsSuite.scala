// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.IO
import cats.effect.Ref
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import lucuma.core.enums
import lucuma.core.enums.ComaOption
import lucuma.core.enums.GuideProbe
import lucuma.core.enums.Instrument
import lucuma.core.enums.LightSinkName
import lucuma.core.enums.M1Source
import lucuma.core.enums.MountGuideOption
import lucuma.core.enums.Site
import lucuma.core.enums.TipTiltSource
import lucuma.core.math.Angle
import lucuma.core.math.Coordinates
import lucuma.core.math.Epoch
import lucuma.core.math.HourAngle
import lucuma.core.math.Offset
import lucuma.core.math.Parallax
import lucuma.core.math.ProperMotion
import lucuma.core.math.RadialVelocity
import lucuma.core.math.Wavelength
import lucuma.core.model.GuideConfig
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.M2GuideConfig.M2GuideOn
import lucuma.core.model.ProbeGuide
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.Enumerated
import lucuma.core.util.TimeSpan
import monocle.Focus
import monocle.Getter
import monocle.Lens
import monocle.syntax.all.*
import mouse.boolean.given
import munit.CatsEffectSuite
import navigate.epics.TestChannel
import navigate.model.AutoparkAowfs
import navigate.model.AutoparkGems
import navigate.model.AutoparkOiwfs
import navigate.model.AutoparkPwfs1
import navigate.model.AutoparkPwfs2
import navigate.model.Distance
import navigate.model.FocalPlaneOffset
import navigate.model.FocalPlaneOffset.DeltaX
import navigate.model.FocalPlaneOffset.DeltaY
import navigate.model.GuiderConfig
import navigate.model.HandsetAdjustment
import navigate.model.InstrumentSpecifics
import navigate.model.Origin
import navigate.model.ResetPointing
import navigate.model.RotatorTrackConfig
import navigate.model.RotatorTrackingMode
import navigate.model.ShortcircuitMountFilter
import navigate.model.ShortcircuitTargetFilter
import navigate.model.SlewOptions
import navigate.model.StopGuide
import navigate.model.Target
import navigate.model.Target.SiderealTarget
import navigate.model.TcsConfig
import navigate.model.TrackingConfig
import navigate.model.ZeroChopThrow
import navigate.model.ZeroGuideOffset
import navigate.model.ZeroInstrumentOffset
import navigate.model.ZeroMountDiffTrack
import navigate.model.ZeroMountOffset
import navigate.model.ZeroSourceDiffTrack
import navigate.model.ZeroSourceOffset
import navigate.model.enums.CentralBafflePosition
import navigate.model.enums.DeployableBafflePosition
import navigate.model.enums.DomeMode
import navigate.model.enums.LightSource
import navigate.model.enums.OiwfsWavelength
import navigate.model.enums.ShutterMode
import navigate.model.enums.VirtualTelescope
import navigate.server.ApplyCommandResult
import navigate.server.acm.CadDirective
import navigate.server.epicsdata
import navigate.server.epicsdata.BinaryOnOff
import navigate.server.epicsdata.BinaryOnOffCapitalized
import navigate.server.epicsdata.BinaryYesNo
import navigate.server.tcs.FollowStatus.Following
import navigate.server.tcs.FollowStatus.NotFollowing
import navigate.server.tcs.ParkStatus.NotParked
import navigate.server.tcs.ParkStatus.Parked
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import TcsBaseController.*
import TestTcsEpicsSystem.{
  GuideConfigState,
  ProbeState,
  ProbeTrackingState,
  ProbeTrackingStateState,
  State,
  TargetChannelsState,
  WfsChannelState,
  WfsObserveChannelState
}

class TcsBaseControllerEpicsSuite extends CatsEffectSuite {

  private val DefaultTimeout: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS)

  private val Tolerance: Double = 1e-6

  private def compareDouble(a: Double, b: Double): Boolean = Math.abs(a - b) < Tolerance

  private given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("navigate-engine")

  test("Mount commands") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.mcsPark
      _        <- ctr.mcsFollow(enable = true)
      rs       <- st.tcs.get
    } yield {
      assert(rs.telescopeParkDir.connected)
      assertEquals(rs.telescopeParkDir.value.get, CadDirective.MARK)
      assert(rs.mountFollow.connected)
      assertEquals(rs.mountFollow.value.get, BinaryOnOff.On.tag)
    }
  }

  test("SCS commands") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.scsFollow(enable = true)
      rs       <- st.tcs.get
    } yield {
      assert(rs.m2Follow.connected)
      assertEquals(rs.m2Follow.value.get, BinaryOnOff.On.tag)
    }
  }

  test("Rotator commands") {
    val testAngle = Angle.fromDoubleDegrees(123.456)

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.rotPark
      _        <- ctr.rotFollow(enable = true)
      _        <- ctr.rotStop(useBrakes = true)
      _        <- ctr.rotMove(testAngle)
      rs       <- st.tcs.get
    } yield {
      assert(rs.rotParkDir.connected)
      assertEquals(rs.rotParkDir.value.get, CadDirective.MARK)
      assert(rs.rotFollow.connected)
      assertEquals(Enumerated[BinaryOnOff].unsafeFromTag(rs.rotFollow.value.get), BinaryOnOff.On)
      assert(rs.rotStopBrake.connected)
      assertEquals(Enumerated[BinaryYesNo].unsafeFromTag(rs.rotStopBrake.value.get),
                   BinaryYesNo.Yes
      )
      assert(rs.rotMoveAngle.connected)
      assert(compareDouble(rs.rotMoveAngle.value.get.toDouble, testAngle.toDoubleDegrees))
    }

  }

  test("Enclosure commands") {
    val testHeight   = 123.456
    val testVentEast = 0.3
    val testVentWest = 0.2

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.ecsCarouselMode(DomeMode.MinVibration,
                                      ShutterMode.Tracking,
                                      testHeight,
                                      domeEnable = true,
                                      shutterEnable = true
                  )
      _        <- ctr.ecsVentGatesMove(testVentEast, testVentWest)
      rs       <- st.tcs.get
    } yield {
      assert(rs.enclosure.ecsDomeMode.connected)
      assert(rs.enclosure.ecsShutterMode.connected)
      assert(rs.enclosure.ecsSlitHeight.connected)
      assert(rs.enclosure.ecsDomeEnable.connected)
      assert(rs.enclosure.ecsShutterEnable.connected)
      assert(rs.enclosure.ecsVentGateEast.connected)
      assert(rs.enclosure.ecsVentGateWest.connected)
      assertEquals(rs.enclosure.ecsDomeMode.value.flatMap(Enumerated[DomeMode].fromTag),
                   DomeMode.MinVibration.some
      )
      assertEquals(rs.enclosure.ecsShutterMode.value.flatMap(Enumerated[ShutterMode].fromTag),
                   ShutterMode.Tracking.some
      )
      assert(rs.enclosure.ecsSlitHeight.value.exists(x => compareDouble(x.toDouble, testHeight)))
      assertEquals(rs.enclosure.ecsDomeEnable.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.enclosure.ecsShutterEnable.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assert(
        rs.enclosure.ecsVentGateEast.value.exists(x => compareDouble(x.toDouble, testVentEast))
      )
      assert(
        rs.enclosure.ecsVentGateWest.value.exists(x => compareDouble(x.toDouble, testVentWest))
      )
    }
  }

  test("Slew command") {
    val targetRa  = "17:01:00.000000"
    val targetDec = "-21:10:59.999999"

    val target = SiderealTarget(
      objectName = "dummy",
      wavelength = Wavelength.fromIntPicometers(400 * 1000),
      coordinates =
        Coordinates.fromHmsDms.getOption(s"$targetRa $targetDec").getOrElse(Coordinates.Zero),
      epoch = Epoch.J2000,
      properMotion = ProperMotion(ProperMotion.μasyRA(1000), ProperMotion.μasyDec(2000)).some,
      radialVelocity = RadialVelocity.fromMetersPerSecond.getOption(BigDecimal.decimal(3000)),
      parallax = Parallax.fromMicroarcseconds(4000).some
    )

    val pwfs1Target = SiderealTarget(
      objectName = "pwfs1Dummy",
      wavelength = Wavelength.fromIntPicometers(550 * 1000),
      coordinates =
        Coordinates.fromHmsDms.getOption("17:00:58.75 -21:10:00.5").getOrElse(Coordinates.Zero),
      epoch = Epoch.J2000,
      properMotion = none,
      radialVelocity = none,
      parallax = none
    )

    val pwfs2Target = SiderealTarget(
      objectName = "pwfs2Dummy",
      wavelength = Wavelength.fromIntPicometers(800 * 1000),
      coordinates =
        Coordinates.fromHmsDms.getOption("17:01:09.999999 -21:10:01.0").getOrElse(Coordinates.Zero),
      epoch = Epoch.J2000,
      properMotion = none,
      radialVelocity = none,
      parallax = none
    )

    val oiwfsTarget = SiderealTarget(
      objectName = "oiwfsDummy",
      wavelength = Wavelength.fromIntPicometers(600 * 1000),
      coordinates = Coordinates.fromHmsDms
        .getOption("17:00:59.999999 -21:10:00.000001")
        .getOrElse(Coordinates.Zero),
      epoch = Epoch.J2000,
      properMotion = none,
      radialVelocity = none,
      parallax = none
    )

    def checkTracking(obtained: ProbeTrackingState, expected: TrackingConfig): Unit = {
      assert(obtained.nodAchopA.connected)
      assert(obtained.nodAchopB.connected)
      assert(obtained.nodBchopA.connected)
      assert(obtained.nodBchopB.connected)
      assertEquals(obtained.nodAchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   expected.nodAchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(obtained.nodAchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   expected.nodAchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(obtained.nodBchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   expected.nodBchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(obtained.nodBchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   expected.nodBchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
    }

    val wfsTracking = TrackingConfig(true, false, false, true)

    val slewOptions = SlewOptions(
      ZeroChopThrow(true),
      ZeroSourceOffset(false),
      ZeroSourceDiffTrack(true),
      ZeroMountOffset(false),
      ZeroMountDiffTrack(true),
      ShortcircuitTargetFilter(false),
      ShortcircuitMountFilter(true),
      ResetPointing(false),
      StopGuide(true),
      ZeroGuideOffset(false),
      ZeroInstrumentOffset(true),
      AutoparkPwfs1(false),
      AutoparkPwfs2(true),
      AutoparkOiwfs(false),
      AutoparkGems(true),
      AutoparkAowfs(false)
    )

    val instrumentSpecifics: InstrumentSpecifics = InstrumentSpecifics(
      iaa = Angle.fromDoubleDegrees(123.45),
      focusOffset = Distance.fromLongMicrometers(2344),
      agName = "gmos",
      origin = Origin(Angle.fromMicroarcseconds(4567), Angle.fromMicroarcseconds(-8901))
    )

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.slew(
                    slewOptions,
                    TcsConfig(
                      target,
                      instrumentSpecifics,
                      GuiderConfig(pwfs1Target, wfsTracking).some,
                      GuiderConfig(pwfs2Target, wfsTracking).some,
                      GuiderConfig(oiwfsTarget, wfsTracking).some,
                      RotatorTrackConfig(Angle.Angle90, RotatorTrackingMode.Tracking),
                      Instrument.GmosNorth
                    )
                  )
      rs       <- st.tcs.get
    } yield {
      // Targets
      checkTarget(rs.sourceA, target)
      checkTarget(rs.pwfs1Target, pwfs1Target)
      checkTarget(rs.pwfs2Target, pwfs2Target)
      checkTarget(rs.oiwfsTarget, oiwfsTarget)

      // WFS probe tracking
      checkTracking(rs.pwfs1Tracking, wfsTracking)
      checkTracking(rs.pwfs2Tracking, wfsTracking)
      checkTracking(rs.oiwfsTracking, wfsTracking)

      rs.wavelSourceA.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No Source A wavelength set"))(v =>
          assertEqualsDouble(
            v,
            target.wavelength.map(_.toMicrometers.value.value.toDouble).getOrElse(0.0),
            1e-6
          )
        )
      rs.wavelOiwfs.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No OIWFS wavelength set"))(v =>
          assertEqualsDouble(v,
                             OiwfsWavelength.GmosOiwfs.wavel.toMicrometers.value.value.toDouble,
                             1e-6
          )
        )

      // Slew Options
      assert(rs.slew.zeroChopThrow.connected)
      assert(rs.slew.zeroSourceOffset.connected)
      assert(rs.slew.zeroSourceDiffTrack.connected)
      assert(rs.slew.zeroMountOffset.connected)
      assert(rs.slew.zeroMountDiffTrack.connected)
      assert(rs.slew.shortcircuitTargetFilter.connected)
      assert(rs.slew.shortcircuitMountFilter.connected)
      assert(rs.slew.resetPointing.connected)
      assert(rs.slew.stopGuide.connected)
      assert(rs.slew.zeroGuideOffset.connected)
      assert(rs.slew.zeroInstrumentOffset.connected)
      assert(rs.slew.autoparkPwfs1.connected)
      assert(rs.slew.autoparkPwfs2.connected)
      assert(rs.slew.autoparkOiwfs.connected)
      assert(rs.slew.autoparkGems.connected)
      assert(rs.slew.autoparkAowfs.connected)
      assertEquals(rs.slew.zeroChopThrow.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.zeroSourceOffset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.zeroSourceDiffTrack.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.zeroMountOffset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.zeroMountDiffTrack.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(
        rs.slew.shortcircuitTargetFilter.value.flatMap(Enumerated[BinaryOnOff].fromTag),
        BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.shortcircuitMountFilter.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.resetPointing.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.stopGuide.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.zeroGuideOffset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.zeroInstrumentOffset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.autoparkPwfs1.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.autoparkPwfs2.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.autoparkOiwfs.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.autoparkGems.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.autoparkAowfs.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )

      // Instrument Specifics
      assert(
        rs.rotator.iaa.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.iaa.toDoubleDegrees)
        )
      )
      assert(
        rs.focusOffset.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.focusOffset.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xa.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.x.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )
      assert(
        rs.origin.xb.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.x.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )
      assert(
        rs.origin.xc.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.x.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )
      assert(
        rs.origin.ya.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.y.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )
      assert(
        rs.origin.yb.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.y.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )
      assert(
        rs.origin.yc.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.y.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )

      // Rotator configuration
      assert(rs.rotator.ipa.connected)
      assert(rs.rotator.system.connected)
      assert(rs.rotator.equinox.connected)
      assert(
        rs.rotator.ipa.value.exists(x => compareDouble(x.toDouble, Angle.Angle90.toDoubleDegrees))
      )
      assert(rs.rotator.system.value.exists(_ === SystemDefault))
      assert(rs.rotator.equinox.value.exists(_ === EquinoxDefault))

      // OIWFS selection
      assert(rs.oiwfsSelect.oiwfsName.connected)
      assert(rs.oiwfsSelect.output.connected)
      assert(rs.oiwfsSelect.oiwfsName.value.exists(_ === "GMOS"))
      assert(rs.oiwfsSelect.output.value.exists(_ === "WFS"))
    }
  }

  test("InstrumentSpecifics command") {
    val instrumentSpecifics: InstrumentSpecifics = InstrumentSpecifics(
      iaa = Angle.fromDoubleDegrees(123.45),
      focusOffset = Distance.fromLongMicrometers(2344),
      agName = "gmos",
      origin = Origin(Angle.fromMicroarcseconds(4567), Angle.fromMicroarcseconds(-8901))
    )

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.instrumentSpecifics(instrumentSpecifics)
      rs       <- st.tcs.get
    } yield {
      assert(
        rs.rotator.iaa.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.iaa.toDoubleDegrees)
        )
      )
      assert(
        rs.focusOffset.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.focusOffset.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xa.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.x.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )
      assert(
        rs.origin.xb.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.x.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )
      assert(
        rs.origin.xc.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.x.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )
      assert(
        rs.origin.ya.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.y.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )
      assert(
        rs.origin.yb.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.y.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )
      assert(
        rs.origin.yc.value.exists(x =>
          compareDouble(
            x.toDouble,
            instrumentSpecifics.origin.y.toLengthInFocalPlane.toMillimeters.value.toDouble
          )
        )
      )
    }
  }

  private def checkTarget(obtained: TargetChannelsState, expected: SiderealTarget): Unit = {
    assert(obtained.objectName.connected)
    assert(obtained.brightness.connected)
    assert(obtained.coord1.connected)
    assert(obtained.coord2.connected)
    assert(obtained.properMotion1.connected)
    assert(obtained.properMotion2.connected)
    assert(obtained.epoch.connected)
    assert(obtained.equinox.connected)
    assert(obtained.parallax.connected)
    assert(obtained.radialVelocity.connected)
    assert(obtained.coordSystem.connected)
    assert(obtained.ephemerisFile.connected)
    assertEquals(obtained.objectName.value, expected.objectName.some)
    assertEquals(
      obtained.coord1.value,
      Some(HourAngle.fromStringHMS.reverseGet(expected.coordinates.ra.toHourAngle))
    )
    assertEquals(
      obtained.coord2.value,
      Some(Angle.fromStringSignedDMS.reverseGet(expected.coordinates.dec.toAngle))
    )
    assert(
      obtained.epoch.value.exists(x => compareDouble(x.toDouble, expected.epoch.epochYear))
    )
    assertEquals(obtained.coordSystem.value, SystemDefault.some)
    assertEquals(obtained.ephemerisFile.value, "".some)

    // Proper motion
    assert(
      obtained.properMotion1.value.exists(x =>
        compareDouble(x.toDouble * 1000.0 * 15.0 * Math.cos(expected.coordinates.dec.toRadians),
                      expected.properMotion.map(_.ra.masy.value.toDouble).orEmpty
        )
      )
    )
    assert(
      obtained.properMotion2.value.exists(x =>
        compareDouble(x.toDouble * 1000.0,
                      expected.properMotion.map(_.dec.masy.value.toDouble).orEmpty
        )
      )
    )
    assert(
      obtained.parallax.value.exists(x =>
        compareDouble(x.toDouble * 1000.0, expected.parallax.map(_.mas.value.toDouble).orEmpty)
      )
    )
    assert(
      obtained.radialVelocity.value.exists(x =>
        compareDouble(x.toDouble,
                      expected.radialVelocity.map(_.toDoubleKilometersPerSecond).orEmpty
        )
      )
    )
  }

  private def testTarget(
    applyCmdL: Getter[TcsBaseController[IO], Target => IO[ApplyCommandResult]],
    l:         Getter[State, TargetChannelsState]
  ): IO[Unit] = {
    val target = SiderealTarget(
      objectName = "Dummy",
      wavelength = none,
      coordinates = Coordinates.unsafeFromRadians(-0.123, 0.321),
      epoch = Epoch.J2000,
      properMotion = ProperMotion(ProperMotion.μasyRA(1000), ProperMotion.μasyDec(2000)).some,
      radialVelocity = RadialVelocity.kilometerspersecond.getOption(30),
      parallax = Parallax.fromMicroarcseconds(500).some
    )

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- applyCmdL.get(ctr)(target)
      rs       <- st.tcs.get
    } yield checkTarget(l.get(rs), target)
  }

  test("pwfs1Target command") {
    testTarget(Getter[TcsBaseController[IO], Target => IO[ApplyCommandResult]](_.pwfs1Target),
               Getter[State, TargetChannelsState](_.pwfs1Target)
    )
  }

  test("pwfs2Target command") {
    testTarget(Getter[TcsBaseController[IO], Target => IO[ApplyCommandResult]](_.pwfs2Target),
               Getter[State, TargetChannelsState](_.pwfs2Target)
    )
  }

  test("oiwfsTarget command") {
    testTarget(Getter[TcsBaseController[IO], Target => IO[ApplyCommandResult]](_.oiwfsTarget),
               Getter[State, TargetChannelsState](_.oiwfsTarget)
    )
  }

  private def testTracking(
    cmdL: Getter[TcsBaseController[IO], TrackingConfig => IO[ApplyCommandResult]],
    l:    Getter[State, ProbeTrackingState]
  ): IO[Unit] = {
    val trackingConfig = TrackingConfig(true, false, false, true)

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- cmdL.get(ctr)(trackingConfig)
      rs       <- st.tcs.get
    } yield {
      assert(l.get(rs).nodAchopA.connected)
      assert(l.get(rs).nodAchopB.connected)
      assert(l.get(rs).nodBchopA.connected)
      assert(l.get(rs).nodBchopB.connected)
      assertEquals(l.get(rs).nodAchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodAchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(l.get(rs).nodAchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodAchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(l.get(rs).nodBchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodBchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(l.get(rs).nodBchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodBchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
    }
  }

  test("pwfs1 probe tracking command") {
    testTracking(
      Getter[TcsBaseController[IO], TrackingConfig => IO[ApplyCommandResult]](_.pwfs1ProbeTracking),
      Getter[State, ProbeTrackingState](_.pwfs1Tracking)
    )
  }

  test("pwfs2 probe tracking command") {
    testTracking(
      Getter[TcsBaseController[IO], TrackingConfig => IO[ApplyCommandResult]](_.pwfs2ProbeTracking),
      Getter[State, ProbeTrackingState](_.pwfs2Tracking)
    )
  }

  test("oiwfs probe tracking command") {
    testTracking(
      Getter[TcsBaseController[IO], TrackingConfig => IO[ApplyCommandResult]](_.oiwfsProbeTracking),
      Getter[State, ProbeTrackingState](_.oiwfsTracking)
    )
  }

  private def testPark(
    cmdL: Getter[TcsBaseController[IO], IO[ApplyCommandResult]],
    l:    Getter[State, ProbeState]
  ): IO[Unit] =
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- cmdL.get(ctr)
      rs       <- st.tcs.get
    } yield {
      assert(l.get(rs).parkDir.connected)
      assertEquals(l.get(rs).parkDir.value, CadDirective.MARK.some)
    }

  test("pwfs1 probe park command") {
    testPark(Getter[TcsBaseController[IO], IO[ApplyCommandResult]](_.pwfs1Park),
             Getter[State, ProbeState](_.pwfs1Probe)
    )
  }

  test("pwfs2 probe park command") {
    testPark(Getter[TcsBaseController[IO], IO[ApplyCommandResult]](_.pwfs2Park),
             Getter[State, ProbeState](_.pwfs2Probe)
    )
  }

  test("oiwfs probe park command") {
    testPark(Getter[TcsBaseController[IO], IO[ApplyCommandResult]](_.oiwfsPark),
             Getter[State, ProbeState](_.oiwfsProbe)
    )
  }

  private def testProbeFollow(
    cmdL: Getter[TcsBaseController[IO], Boolean => IO[ApplyCommandResult]],
    l:    Getter[State, ProbeState]
  ): IO[Unit] =
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- cmdL.get(ctr)(true)
      r1       <- st.tcs.get
      _        <- cmdL.get(ctr)(false)
      r2       <- st.tcs.get
    } yield {
      assert(l.get(r1).follow.connected)
      assertEquals(l.get(r1).follow.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(l.get(r2).follow.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
    }

  test("pwfs1 probe follow command") {
    testProbeFollow(Getter[TcsBaseController[IO], Boolean => IO[ApplyCommandResult]](_.pwfs1Follow),
                    Getter[State, ProbeState](_.pwfs1Probe)
    )
  }

  test("pwfs2 probe follow command") {
    testProbeFollow(Getter[TcsBaseController[IO], Boolean => IO[ApplyCommandResult]](_.pwfs2Follow),
                    Getter[State, ProbeState](_.pwfs2Probe)
    )
  }

  test("oiwfs probe follow command") {
    testProbeFollow(Getter[TcsBaseController[IO], Boolean => IO[ApplyCommandResult]](_.oiwfsFollow),
                    Getter[State, ProbeState](_.oiwfsProbe)
    )
  }

  def setWfsTrackingState(
    r: Ref[IO, TestTcsEpicsSystem.State],
    l: Lens[TestTcsEpicsSystem.State, ProbeTrackingStateState]
  ): IO[Unit] = r.update(
    l.replace(
      ProbeTrackingStateState(
        TestChannel.State.of("On"),
        TestChannel.State.of("Off"),
        TestChannel.State.of("Off"),
        TestChannel.State.of("On")
      )
    )
  )

  private def probeEncode(gp: GuideProbe): String = gp match {
    case enums.GuideProbe.GmosOIWFS | enums.GuideProbe.Flamingos2OIWFS => "OIWFS"
    case a                                                             => a.tag.toUpperCase
  }

  private def checkGuide(obtained: State, expected: TelescopeGuideConfig): Unit = {
    assert(obtained.m1Guide.connected)
    assert(obtained.m1GuideConfig.source.connected)
    assert(obtained.m1GuideConfig.frames.connected)
    assert(obtained.m1GuideConfig.weighting.connected)
    assert(obtained.m1GuideConfig.filename.connected)
    assert(obtained.m2Guide.connected)
    assert(obtained.m2GuideConfig.source.connected)
    assert(obtained.m2GuideConfig.beam.connected)
    assert(obtained.m2GuideConfig.filter.connected)
    assert(obtained.m2GuideConfig.samplefreq.connected)
    assert(obtained.m2GuideConfig.reset.connected)
    assert(obtained.m2GuideMode.connected)
    assert(obtained.m2GuideReset.connected)
    assert(obtained.mountGuide.mode.connected)
    assert(obtained.mountGuide.source.connected)
    assert(obtained.probeGuideMode.state.connected)
    expected.m1Guide match {
      case M1GuideConfig.M1GuideOff        =>
        assertEquals(obtained.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                     BinaryOnOff.Off.some
        )
      case M1GuideConfig.M1GuideOn(source) =>
        assertEquals(obtained.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                     BinaryOnOff.On.some
        )
        assertEquals(obtained.m1GuideConfig.source.value, source.tag.toUpperCase.some)
    }
    assertEquals(obtained.m1GuideConfig.frames.value.flatMap(_.toIntOption), 1.some)
    assertEquals(obtained.m1GuideConfig.weighting.value, "none".some)
    assertEquals(obtained.m1GuideConfig.filename.value, "".some)
    expected.m2Guide match {
      case M2GuideConfig.M2GuideOff =>
        assertEquals(obtained.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                     BinaryOnOff.Off.some
        )
      case M2GuideOn(coma, sources) =>
        assertEquals(obtained.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                     BinaryOnOff.On.some
        )
        assertEquals(obtained.m2GuideConfig.source.value, sources.last.tag.toUpperCase.some)
        assertEquals(obtained.m2GuideConfig.beam.value, "A".some)
        assertEquals(obtained.m2GuideConfig.filter.value, "raw".some)
        assertEquals(obtained.m2GuideConfig.samplefreq.value.flatMap(_.toDoubleOption), 200.0.some)
        assertEquals(obtained.m2GuideConfig.reset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                     BinaryOnOff.Off.some
        )
        assertEquals(obtained.m2GuideMode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                     (coma === ComaOption.ComaOn).fold(BinaryOnOff.On, BinaryOnOff.Off).some
        )
        assertEquals(obtained.m2GuideReset.value, CadDirective.MARK.some)
    }
    expected.mountGuide match {
      case MountGuideOption.MountGuideOff =>
        assertEquals(obtained.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                     BinaryOnOff.Off.some
        )
      case MountGuideOption.MountGuideOn  =>
        assertEquals(obtained.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                     BinaryOnOff.On.some
        )
        assertEquals(obtained.mountGuide.source.value, "SCS".some)
    }
    expected.probeGuide
      .map { case ProbeGuide(from, to) =>
        assertEquals(obtained.probeGuideMode.state.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                     BinaryOnOff.On.some
        )
        assertEquals(obtained.probeGuideMode.from.value, probeEncode(from).some)
        assertEquals(obtained.probeGuideMode.to.value, probeEncode(to).some)
      }
      .getOrElse {
        assertEquals(obtained.probeGuideMode.state.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                     BinaryOnOff.Off.some
        )
      }
  }

  private val noGuideConfig: TelescopeGuideConfig = TelescopeGuideConfig(
    mountGuide = MountGuideOption.MountGuideOff,
    m1Guide = M1GuideConfig.M1GuideOff,
    m2Guide = M2GuideConfig.M2GuideOff,
    dayTimeMode = Some(false),
    probeGuide = none
  )

  private val p1GuideConfig: TelescopeGuideConfig = TelescopeGuideConfig(
    mountGuide = MountGuideOption.MountGuideOn,
    m1Guide = M1GuideConfig.M1GuideOn(M1Source.PWFS1),
    m2Guide = M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.PWFS1)),
    dayTimeMode = Some(false),
    probeGuide = none
  )

  private val p2GuideConfig: TelescopeGuideConfig = TelescopeGuideConfig(
    mountGuide = MountGuideOption.MountGuideOn,
    m1Guide = M1GuideConfig.M1GuideOn(M1Source.PWFS2),
    m2Guide = M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.PWFS2)),
    dayTimeMode = Some(false),
    probeGuide = none
  )

  private val oiGuideConfig: TelescopeGuideConfig = TelescopeGuideConfig(
    mountGuide = MountGuideOption.MountGuideOn,
    m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
    m2Guide = M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
    dayTimeMode = Some(false),
    probeGuide = none
  )

  test("Enable and disable guiding with default gains") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- setWfsTrackingState(st.tcs, Focus[State](_.oiwfsTrackingState))
      _        <- ctr.enableGuide(oiGuideConfig)
      r1       <- st.tcs.get
      p1_1     <- st.p1.get
      p2_1     <- st.p2.get
      oi_1     <- st.oiw.get
      _        <- ctr.disableGuide
      r2       <- st.tcs.get
    } yield {
      checkGuide(r1, oiGuideConfig)
      assert(p1_1.reset.connected)
      assert(p2_1.reset.connected)
      assert(oi_1.reset.connected)
      assertEquals(p1_1.reset.value, 1.0.some)
      assertEquals(p2_1.reset.value, 1.0.some)
      assertEquals(oi_1.reset.value, 1.0.some)
      checkGuide(r2, noGuideConfig)
    }
  }

  test("Enable and disable guiding in day mode") {
    val guideCfg = oiGuideConfig.copy(dayTimeMode = true.some)

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- setWfsTrackingState(st.tcs, Focus[State](_.oiwfsTrackingState))
      _        <- ctr.enableGuide(guideCfg)
      r1       <- st.tcs.get
      p1_1     <- st.p1.get
      p2_1     <- st.p2.get
      oi_1     <- st.oiw.get
      _        <- ctr.disableGuide
      r2       <- st.tcs.get
    } yield {
      checkGuide(r1, guideCfg)
      assert(p1_1.tipGain.connected)
      assert(p1_1.tiltGain.connected)
      assert(p1_1.focusGain.connected)
      assert(p2_1.tipGain.connected)
      assert(p2_1.tiltGain.connected)
      assert(p2_1.focusGain.connected)
      assert(oi_1.tipGain.connected)
      assert(oi_1.tiltGain.connected)
      assert(oi_1.focusGain.connected)

      assertEquals(p1_1.tipGain.value, "0.0".some)
      assertEquals(p1_1.tiltGain.value, "0.0".some)
      assertEquals(p1_1.focusGain.value, "0.0".some)

      assertEquals(p2_1.tipGain.value, "0.0".some)
      assertEquals(p2_1.tiltGain.value, "0.0".some)
      assertEquals(p2_1.focusGain.value, "0.0".some)

      assertEquals(oi_1.tipGain.value, "0.0".some)
      assertEquals(oi_1.tiltGain.value, "0.0".some)
      assertEquals(oi_1.focusGain.value, "0.0".some)
    }
  }

  test("Enable and disable guiding") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- setWfsTrackingState(st.tcs, Focus[State](_.oiwfsTrackingState))
      _        <- ctr.enableGuide(oiGuideConfig)
      r1       <- st.tcs.get
      _        <- ctr.disableGuide
      r2       <- st.tcs.get
    } yield {
      checkGuide(r1, oiGuideConfig)
      checkGuide(r2, noGuideConfig)
    }
  }

  test("Set guide mode OIWFS to OIWFS") {
    val guideCfg = oiGuideConfig.copy(
      probeGuide = ProbeGuide(GuideProbe.GmosOIWFS, GuideProbe.GmosOIWFS).some
    )

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.enableGuide(guideCfg)
      r1       <- st.tcs.get
    } yield {
      assert(r1.probeGuideMode.state.connected)

      assertEquals(r1.probeGuideMode.state.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.probeGuideMode.from.value, "OIWFS".some)
      assertEquals(r1.probeGuideMode.to.value, "OIWFS".some)
    }
  }

  test("Set guide mode PWFS1 to PWFS2") {
    val guideCfg = oiGuideConfig.copy(
      probeGuide = ProbeGuide(GuideProbe.PWFS1, GuideProbe.PWFS2).some
    )

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.enableGuide(guideCfg)
      r1       <- st.tcs.get
    } yield {
      assert(r1.probeGuideMode.state.connected)

      assertEquals(r1.probeGuideMode.state.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.probeGuideMode.from.value, "PWFS1".some)
      assertEquals(r1.probeGuideMode.to.value, "PWFS2".some)
    }
  }

  private def testWfsStart(
    cmdL: Getter[TcsBaseController[IO], TimeSpan => IO[ApplyCommandResult]],
    l:    Getter[State, WfsObserveChannelState],
    fnL:  Getter[StateRefs[IO], IO[TestChannel.State[String]]]
  ): IO[Unit] = {
    val testVal          = TimeSpan.unsafeFromMicroseconds(5000)
    val expectedFilename = "data/200Hz.fits"

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(defaultGuideState))
      _        <- cmdL.get(ctr)(testVal)
      rs       <- st.tcs.get
      fn       <- fnL.get(st)
    } yield {
      assert(l.get(rs).path.connected)
      assert(l.get(rs).label.connected)
      assert(l.get(rs).output.connected)
      assert(l.get(rs).options.connected)
      assert(l.get(rs).fileName.connected)
      assert(l.get(rs).interval.connected)
      assert(l.get(rs).numberOfExposures.connected)
      assertEquals(l.get(rs).interval.value.flatMap(_.toDoubleOption),
                   testVal.toSeconds.toDouble.some
      )
      assertEquals(l.get(rs).numberOfExposures.value.flatMap(_.toIntOption), -1.some)
      l.get(rs)
        .interval
        .value
        .flatMap(_.toDoubleOption)
        .map(assertEqualsDouble(_, testVal.toSeconds.toDouble, 1e-6))
        .getOrElse(fail("No interval value set"))
      assertEquals(fn.value, expectedFilename.some)
    }
  }

  test("Start PWFS1 readout") {
    testWfsStart(
      Getter[TcsBaseController[IO], TimeSpan => IO[ApplyCommandResult]](_.pwfs1Observe),
      Getter[State, WfsObserveChannelState](_.pwfs1.observe),
      Getter[StateRefs[IO], IO[TestChannel.State[String]]](_.tcs.get.map(_.pwfs1.signalProc))
    )
  }

  test("Start PWFS2 readout") {
    testWfsStart(
      Getter[TcsBaseController[IO], TimeSpan => IO[ApplyCommandResult]](_.pwfs2Observe),
      Getter[State, WfsObserveChannelState](_.pwfs2.observe),
      Getter[StateRefs[IO], IO[TestChannel.State[String]]](_.tcs.get.map(_.pwfs2.signalProc))
    )
  }

  test("Start OIWFS readout") {
    testWfsStart(
      Getter[TcsBaseController[IO], TimeSpan => IO[ApplyCommandResult]](_.oiwfsObserve),
      Getter[State, WfsObserveChannelState](_.oiwfs.observe),
      Getter[StateRefs[IO], IO[TestChannel.State[String]]](_.oi.get.map(_.darkFilename))
    )
  }

  private def testWfsStop(
    cmdL: Getter[TcsBaseController[IO], IO[ApplyCommandResult]],
    l:    Getter[State, WfsChannelState]
  ): IO[Unit] =
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- cmdL.get(ctr)
      rs       <- st.tcs.get
    } yield {
      assert(l.get(rs).stop.connected)
      assertEquals(l.get(rs).stop.value, CadDirective.MARK.some)
    }

  test("Stop PWFS1 readout") {
    testWfsStop(Getter[TcsBaseController[IO], IO[ApplyCommandResult]](_.pwfs1StopObserve),
                Getter[State, WfsChannelState](_.pwfs1)
    )
  }

  test("Stop PWFS2 readout") {
    testWfsStop(Getter[TcsBaseController[IO], IO[ApplyCommandResult]](_.pwfs2StopObserve),
                Getter[State, WfsChannelState](_.pwfs2)
    )
  }

  test("Stop OIWFS readout") {
    testWfsStop(Getter[TcsBaseController[IO], IO[ApplyCommandResult]](_.oiwfsStopObserve),
                Getter[State, WfsChannelState](_.oiwfs)
    )
  }

  test("Start HRWFS exposures") {
    val testVal = TimeSpan.unsafeFromMicroseconds(12345)

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.hrwfsObserve(testVal)
      rs       <- st.ac.get
    } yield {
      assert(rs.expTime.connected)
      assert(rs.dhsStream.connected)
      assert(rs.dhsOption.connected)
      assert(rs.frameCount.connected)
      assert(rs.expTime.connected)
      assertEquals(rs.expTime.value.flatMap(_.toDoubleOption), testVal.toSeconds.toDouble.some)
      assertEquals(rs.frameCount.value.flatMap(_.toIntOption), -1.some)
    }
  }

  test("Stop HRWFS exposures") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.hrwfsStopObserve
      rs       <- st.ac.get
    } yield {
      assert(rs.stopDir.connected)
      assertEquals(rs.stopDir.value, CadDirective.MARK.some)
    }
  }

  private val defaultGuideState = GuideConfigState(
    pwfs1Integrating = TestChannel.State.of(BinaryYesNo.No),
    pwfs2Integrating = TestChannel.State.of(BinaryYesNo.No),
    oiwfsIntegrating = TestChannel.State.of(BinaryYesNo.No),
    m2State = TestChannel.State.of(BinaryOnOff.Off),
    absorbTipTilt = TestChannel.State.of(0),
    m2ComaCorrection = TestChannel.State.of(BinaryOnOff.Off),
    m1State = TestChannel.State.of(BinaryOnOff.Off),
    m1Source = TestChannel.State.of(""),
    p1ProbeGuide = TestChannel.State.of(0.0),
    p2ProbeGuide = TestChannel.State.of(0.0),
    oiProbeGuide = TestChannel.State.of(0.0),
    p1ProbeGuided = TestChannel.State.of(0.0),
    p2ProbeGuided = TestChannel.State.of(0.0),
    oiProbeGuided = TestChannel.State.of(0.0),
    mountP1Weight = TestChannel.State.of(0.0),
    mountP2Weight = TestChannel.State.of(0.0),
    m2P1Guide = TestChannel.State.of("OFF"),
    m2P2Guide = TestChannel.State.of("OFF"),
    m2OiGuide = TestChannel.State.of("OFF"),
    m2AoGuide = TestChannel.State.of("OFF")
  )

  private val guideWithP1State = defaultGuideState.copy(
    pwfs1Integrating = TestChannel.State.of(BinaryYesNo.Yes),
    m2State = TestChannel.State.of(BinaryOnOff.On),
    absorbTipTilt = TestChannel.State.of(1),
    m2ComaCorrection = TestChannel.State.of(BinaryOnOff.On),
    m1State = TestChannel.State.of(BinaryOnOff.On),
    m1Source = TestChannel.State.of("PWFS1"),
    m2P1Guide = TestChannel.State.of("RAW A-AUTO B-OFF C-OFF")
  )

  private val guideWithP2State = defaultGuideState.copy(
    pwfs2Integrating = TestChannel.State.of(BinaryYesNo.Yes),
    m2State = TestChannel.State.of(BinaryOnOff.On),
    absorbTipTilt = TestChannel.State.of(1),
    m2ComaCorrection = TestChannel.State.of(BinaryOnOff.On),
    m1State = TestChannel.State.of(BinaryOnOff.On),
    m1Source = TestChannel.State.of("PWFS2"),
    m2P2Guide = TestChannel.State.of("RAW A-AUTO B-OFF C-OFF")
  )

  private val guideWithOiState = defaultGuideState.copy(
    oiwfsIntegrating = TestChannel.State.of(BinaryYesNo.Yes),
    m2State = TestChannel.State.of(BinaryOnOff.On),
    absorbTipTilt = TestChannel.State.of(1),
    m2ComaCorrection = TestChannel.State.of(BinaryOnOff.On),
    m1State = TestChannel.State.of(BinaryOnOff.On),
    m1Source = TestChannel.State.of("OIWFS"),
    m2OiGuide = TestChannel.State.of("RAW A-AUTO B-OFF C-OFF")
  )

  test("Read guide state") {
    val testGuide = GuideState(
      MountGuideOption.MountGuideOn,
      M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
      false,
      false,
      true,
      false
    )

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(guideWithOiState))
      g        <- ctr.getGuideState
      r1       <- st.tcs.get
    } yield {
      assert(r1.guideStatus.m2State.connected)
      assert(r1.guideStatus.absorbTipTilt.connected)
      assert(r1.guideStatus.m2ComaCorrection.connected)
      assert(r1.guideStatus.m1State.connected)
      assert(r1.guideStatus.m1Source.connected)
      assert(r1.guideStatus.m2OiGuide.connected)
      assertEquals(g, testGuide)
    }
  }

  test("Read telescope state") {
    val testTelState = TelescopeState(
      mount = MechSystemState(NotParked, Following),
      scs = MechSystemState(NotParked, Following),
      crcs = MechSystemState(NotParked, Following),
      pwfs1 = MechSystemState(Parked, NotFollowing),
      pwfs2 = MechSystemState(Parked, NotFollowing),
      oiwfs = MechSystemState(NotParked, Following)
    )

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- st.mcs.update(_.focus(_.follow).replace(TestChannel.State.of("ON")))
      _        <- st.scs.update(_.focus(_.follow).replace(TestChannel.State.of("YES")))
      _        <- st.crcs.update(_.focus(_.follow).replace(TestChannel.State.of("ON")))
      _        <- st.ags.update(
                    _.copy(oiParked = TestChannel.State.of(0), oiFollow = TestChannel.State.of("ON"))
                  )
      s        <- ctr.getTelescopeState
      r0       <- st.mcs.get
      r1       <- st.scs.get
      r2       <- st.crcs.get
      r3       <- st.ags.get
    } yield {
      assert(r0.follow.connected)
      assert(r1.follow.connected)
      assert(r2.follow.connected)
      assert(r3.p1Parked.connected)
      assert(r3.p1Follow.connected)
      assert(r3.p2Parked.connected)
      assert(r3.p2Follow.connected)
      assert(r3.oiParked.connected)
      assert(r3.oiFollow.connected)
      assertEquals(s, testTelState)
    }
  }

  test("Read guide quality values") {
    val testGuideQuality = GuidersQualityValues(
      GuidersQualityValues.GuiderQuality(1000, false),
      GuidersQualityValues.GuiderQuality(500, true),
      GuidersQualityValues.GuiderQuality(1500, false)
    )
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- st.p1.update(
                    _.copy(
                      flux = TestChannel.State.of(testGuideQuality.pwfs1.flux),
                      centroid = TestChannel.State.of(1)
                    )
                  )
      _        <- st.p2.update(
                    _.copy(
                      flux = TestChannel.State.of(testGuideQuality.pwfs2.flux),
                      centroid = TestChannel.State.of(0)
                    )
                  )
      _        <- st.oiw.update(
                    _.copy(
                      flux = TestChannel.State.of(testGuideQuality.oiwfs.flux),
                      centroid = TestChannel.State.of(65536)
                    )
                  )
      g        <- ctr.getGuideQuality
      rp1      <- st.p1.get
      rp2      <- st.p2.get
      roi      <- st.oiw.get
    } yield {
      assert(rp1.flux.connected)
      assert(rp1.centroid.connected)
      assert(rp2.flux.connected)
      assert(rp2.centroid.connected)
      assert(roi.flux.connected)
      assert(roi.centroid.connected)
      assertEquals(g, testGuideQuality)
    }
  }

  private def testAutoQl(
    obsCmdL:       Getter[TcsBaseController[IO], TimeSpan => IO[ApplyCommandResult]],
    stpCmdL:       Getter[TcsBaseController[IO], IO[ApplyCommandResult]],
    obStL:         Getter[State, WfsObserveChannelState],
    z2m2L:         Getter[StateRefs[IO], IO[TestChannel.State[String]]],
    guideCfgState: GuideConfigState
  ): IO[Unit] = {
    val testExpTime = TimeSpan.unsafeFromMicroseconds(12345)
    val guideCfg    = TelescopeGuideConfig(
      mountGuide = MountGuideOption.MountGuideOn,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      m2Guide = M2GuideOn(ComaOption.ComaOn,
                          Set(TipTiltSource.PWFS1, TipTiltSource.PWFS2, TipTiltSource.OIWFS)
      ),
      dayTimeMode = Some(false),
      probeGuide = none
    )

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(defaultGuideState))
      _        <- obsCmdL.get(ctr)(testExpTime)
      r00      <- st.tcs.get
      s00      <- z2m2L.get(st)
      _        <- ctr.enableGuide(guideCfg)
      r01      <- st.tcs.get
      s01      <- z2m2L.get(st)
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(guideCfgState))
      _        <- stpCmdL.get(ctr)
      r02      <- st.tcs.get
      s02      <- z2m2L.get(st)
      _        <- ctr.disableGuide
      r03      <- st.tcs.get
      s03      <- z2m2L.get(st)
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(defaultGuideState))
      _        <- ctr.enableGuide(guideCfg)
      r10      <- st.tcs.get
      s10      <- z2m2L.get(st)
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(guideCfgState))
      _        <- obsCmdL.get(ctr)(testExpTime)
      r11      <- st.tcs.get
      s11      <- z2m2L.get(st)
      _        <- ctr.disableGuide
      r12      <- st.tcs.get
      s12      <- z2m2L.get(st)
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(defaultGuideState))
      _        <- ctr.oiwfsStopObserve
      r13      <- st.tcs.get
      s13      <- z2m2L.get(st)
    } yield {
      assertEquals(obStL.get(r00).output.value, "QL".some)
      assertEquals(obStL.get(r00).options.value, "DHS".some)
      assertEquals(s00.value, "0".some)
      assertEquals(obStL.get(r01).output.value, "".some)
      assertEquals(obStL.get(r01).options.value, "NONE".some)
      assertEquals(s01.value, "1".some)
      assertEquals(obStL.get(r02).output.value, "".some)
      assertEquals(obStL.get(r02).options.value, "NONE".some)
      assertEquals(s02.value, "1".some)
      assertEquals(obStL.get(r03).output.value, "".some)
      assertEquals(obStL.get(r03).options.value, "NONE".some)
      assertEquals(s03.value, "1".some)
      assertEquals(obStL.get(r10).output.value, "".some)
      assertEquals(obStL.get(r10).options.value, "NONE".some)
      assertEquals(s10.value, "1".some)
      assertEquals(obStL.get(r11).output.value, "".some)
      assertEquals(obStL.get(r11).options.value, "NONE".some)
      assertEquals(s11.value, "1".some)
      assertEquals(obStL.get(r12).output.value, "QL".some)
      assertEquals(obStL.get(r12).options.value, "DHS".some)
      assertEquals(s12.value, "0".some)
      assertEquals(obStL.get(r13).output.value, "QL".some)
      assertEquals(obStL.get(r13).options.value, "DHS".some)
      assertEquals(s13.value, "0".some)
    }
  }

  test("Automatically set PWFS1 QL") {
    testAutoQl(
      Getter[TcsBaseController[IO], TimeSpan => IO[ApplyCommandResult]](_.pwfs1Observe),
      Getter[TcsBaseController[IO], IO[ApplyCommandResult]](_.pwfs1StopObserve),
      Getter[State, WfsObserveChannelState](_.pwfs1.observe),
      Getter[StateRefs[IO], IO[TestChannel.State[String]]](
        _.tcs.get.map(_.pwfs1.closedLoop.zernikes2m2)
      ),
      guideWithP1State
    )
  }

  test("Automatically set PWFS2 QL") {
    testAutoQl(
      Getter[TcsBaseController[IO], TimeSpan => IO[ApplyCommandResult]](_.pwfs2Observe),
      Getter[TcsBaseController[IO], IO[ApplyCommandResult]](_.pwfs2StopObserve),
      Getter[State, WfsObserveChannelState](_.pwfs2.observe),
      Getter[StateRefs[IO], IO[TestChannel.State[String]]](
        _.tcs.get.map(_.pwfs2.closedLoop.zernikes2m2)
      ),
      guideWithP2State
    )
  }

  test("Automatically set OIWFS QL") {
    testAutoQl(
      Getter[TcsBaseController[IO], TimeSpan => IO[ApplyCommandResult]](_.oiwfsObserve),
      Getter[TcsBaseController[IO], IO[ApplyCommandResult]](_.oiwfsStopObserve),
      Getter[State, WfsObserveChannelState](_.oiwfs.observe),
      Getter[StateRefs[IO], IO[TestChannel.State[String]]](_.oi.get.map(_.z2m2)),
      guideWithOiState
    )
  }

  test("Set baffles") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.baffles(CentralBafflePosition.Open, DeployableBafflePosition.ThermalIR)
      r0       <- st.tcs.get
      _        <- ctr.baffles(CentralBafflePosition.Closed, DeployableBafflePosition.NearIR)
      r1       <- st.tcs.get
      _        <- ctr.baffles(CentralBafflePosition.Open, DeployableBafflePosition.Visible)
      r2       <- st.tcs.get
      _        <- ctr.baffles(CentralBafflePosition.Open, DeployableBafflePosition.Extended)
      r3       <- st.tcs.get
    } yield {
      assert(r0.m2Baffles.centralBaffle.connected)
      assert(r0.m2Baffles.deployBaffle.connected)
      assertEquals(r0.m2Baffles.centralBaffle.value, "Open".some)
      assertEquals(r0.m2Baffles.deployBaffle.value, "Retracted".some)
      assertEquals(r1.m2Baffles.centralBaffle.value, "Closed".some)
      assertEquals(r1.m2Baffles.deployBaffle.value, "Near IR".some)
      assertEquals(r2.m2Baffles.deployBaffle.value, "Visible".some)
      assertEquals(r3.m2Baffles.deployBaffle.value, "Extended".some)
    }
  }

  test("Park M1") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.m1Park
      r0       <- st.tcs.get
    } yield {
      assert(r0.m1Cmds.park.connected)
      assertEquals(r0.m1Cmds.park.value, "PARK".some)
    }
  }

  test("Unpark M1") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.m1Unpark
      r0       <- st.tcs.get
    } yield {
      assert(r0.m1Cmds.park.connected)
      assertEquals(r0.m1Cmds.park.value, "UNPARK".some)
    }
  }

  test("Zero M1 figure") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.m1ZeroFigure
      r0       <- st.tcs.get
    } yield {
      assert(r0.m1Cmds.zero.connected)
      assertEquals(r0.m1Cmds.zero.value, "FIGURE".some)
    }
  }

  test("Enable M1 updates") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.m1UpdateOn
      r0       <- st.tcs.get
    } yield {
      assert(r0.m1Cmds.figUpdates.connected)
      assert(r0.m1Cmds.aoEnable.connected)
      assertEquals(r0.m1Cmds.figUpdates.value, "On".some)
      assertEquals(r0.m1Cmds.aoEnable.value, BinaryOnOffCapitalized.On.some)
    }
  }

  test("Disable M1 updates") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.m1UpdateOff
      r0       <- st.tcs.get
    } yield {
      assert(r0.m1Cmds.aoEnable.connected)
      assertEquals(r0.m1Cmds.aoEnable.value, BinaryOnOffCapitalized.Off.some)
    }
  }

  test("Load M1 AO figure") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.m1LoadAoFigure
      r0       <- st.tcs.get
    } yield {
      assert(r0.m1Cmds.loadModelFile.connected)
      assertEquals(r0.m1Cmds.loadModelFile.value, "AO".some)
    }
  }

  test("Load M1 non-AO figure") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- ctr.m1LoadNonAoFigure
      r0       <- st.tcs.get
    } yield {
      assert(r0.m1Cmds.loadModelFile.connected)
      assertEquals(r0.m1Cmds.loadModelFile.value, "non-AO".some)
    }
  }

  test("Don't try to read GN instruments ports at GS") {
    for {
      x        <- createController(site = Site.GS)
      (st, ctr) = x
      _        <- ctr.getInstrumentPorts
      r0       <- st.ags.get
    } yield {
      assert(r0.f2Port.connected)
      assert(r0.ghostPort.connected)
      assert(r0.gmosPort.connected)
      assert(r0.gsaoiPort.connected)
      assert(!r0.gnirsPort.connected)
      assert(!r0.gpiPort.connected)
      assert(!r0.nifsPort.connected)
      assert(!r0.niriPort.connected)
    }
  }

  test("Don't try to read GS instruments ports at GN") {
    for {
      x        <- createController(site = Site.GN)
      (st, ctr) = x
      _        <- ctr.getInstrumentPorts
      r0       <- st.ags.get
    } yield {
      assert(!r0.f2Port.connected)
      assert(!r0.ghostPort.connected)
      assert(r0.gmosPort.connected)
      assert(!r0.gsaoiPort.connected)
      assert(r0.gnirsPort.connected)
      assert(r0.gpiPort.connected)
      assert(r0.nifsPort.connected)
      assert(r0.niriPort.connected)
    }
  }

  test("Configure light path") {
    for {
      x        <- createController(site = Site.GN)
      (st, ctr) = x
      _        <- st.ags.update(_.focus(_.gmosPort.value).replace(3.some))
      _        <- st.ags.update(
                    _.focus(_.aoParked.value)
                      .replace(0.some)
                      .focus(_.aoName.value)
                      .replace("IN".some)
                      .focus(_.hwParked.value)
                      .replace(0.some)
                      .focus(_.hwName.value)
                      .replace("IN".some)
                      .focus(_.sfParked.value)
                      .replace(1.some)
                  )
      _        <- ctr.lightPath(LightSource.Sky, LightSinkName.Gmos)
      r0       <- st.tcs.get
      _        <- st.ags.update(
                    _.focus(_.aoParked.value)
                      .replace(1.some)
                      .focus(_.hwParked.value)
                      .replace(0.some)
                      .focus(_.hwName.value)
                      .replace("IN".some)
                      .focus(_.sfParked.value)
                      .replace(0.some)
                  )
      _        <- ctr.lightPath(LightSource.AO, LightSinkName.Gmos)
      r1       <- st.tcs.get
      _        <- st.ags.update(
                    _.focus(_.aoParked.value)
                      .replace(0.some)
                      .focus(_.aoName.value)
                      .replace("IN".some)
                      .focus(_.hwParked.value)
                      .replace(0.some)
                      .focus(_.hwName.value)
                      .replace("IN".some)
                      .focus(_.sfParked.value)
                      .replace(0.some)
                  )
      _        <- st.tcs.update(_.focus(_.aoFoldMech.parkDir.value).replace(CadDirective.CLEAR.some))
      _        <- ctr.lightPath(LightSource.GCAL, LightSinkName.Gmos)
      r2       <- st.tcs.get
      _        <- st.ags.update(
                    _.focus(_.aoParked.value)
                      .replace(0.some)
                      .focus(_.aoName.value)
                      .replace("IN".some)
                      .focus(_.hwParked.value)
                      .replace(1.some)
                      .focus(_.sfParked.value)
                      .replace(0.some)
                  )
      _        <- ctr.lightPath(LightSource.Sky, LightSinkName.Ac)
      r3       <- st.tcs.get
      _        <- st.ags.update(
                    _.focus(_.aoParked.value)
                      .replace(1.some)
                      .focus(_.hwParked.value)
                      .replace(1.some)
                      .focus(_.sfParked.value)
                      .replace(0.some)
                  )
      _        <- ctr.lightPath(LightSource.AO, LightSinkName.Ac)
      r4       <- st.tcs.get
    } yield {
      assert(r0.aoFoldMech.parkDir.connected)
      assert(r0.scienceFoldMech.position.connected)
      assert(r0.hrwfsMech.parkDir.connected)
      assertEquals(r0.scienceFoldMech.position.value, "gmos3".some)
      assert(r1.aoFoldMech.position.connected)
      assertEquals(r1.aoFoldMech.position.value, "IN".some)
      assertEquals(r1.scienceFoldMech.position.value, "ao2gmos3".some)
      assertEquals(r2.aoFoldMech.parkDir.value, CadDirective.CLEAR.some)
      assertEquals(r2.scienceFoldMech.position.value, "gcal2gmos3".some)
      assert(r3.hrwfsMech.position.connected)
      assertEquals(r3.scienceFoldMech.parkDir.value, CadDirective.MARK.some)
      assertEquals(r3.hrwfsMech.position.value, "IN".some)
      assertEquals(r4.aoFoldMech.position.value, "IN".some)
      assertEquals(r4.scienceFoldMech.position.value, "ao2ac".some)
    }
  }

  test("Don't reconfigure light path") {
    for {
      x        <- createController(site = Site.GN)
      (st, ctr) = x
      _        <- st.ags.update(_.focus(_.gmosPort.value).replace(3.some))
      _        <- st.ags.update(
                    _.focus(_.aoParked.value)
                      .replace(1.some)
                      .focus(_.hwParked.value)
                      .replace(1.some)
                      .focus(_.sfParked.value)
                      .replace(0.some)
                      .focus(_.sfName.value)
                      .replace("gmos3".some)
                  )
      _        <- ctr.lightPath(LightSource.Sky, LightSinkName.Gmos)
      _        <- st.ags.update(
                    _.focus(_.aoParked.value)
                      .replace(0.some)
                      .focus(_.aoName.value)
                      .replace("IN".some)
                      .focus(_.hwParked.value)
                      .replace(1.some)
                      .focus(_.sfParked.value)
                      .replace(0.some)
                      .focus(_.sfName.value)
                      .replace("ao2gmos3".some)
                  )
      _        <- ctr.lightPath(LightSource.AO, LightSinkName.Gmos)
      _        <- st.ags.update(
                    _.focus(_.aoParked.value)
                      .replace(0.some)
                      .focus(_.aoName.value)
                      .replace("IN".some)
                      .focus(_.hwParked.value)
                      .replace(1.some)
                      .focus(_.sfParked.value)
                      .replace(0.some)
                      .focus(_.sfName.value)
                      .replace("gcal2gmos3".some)
                  )
      _        <- ctr.lightPath(LightSource.GCAL, LightSinkName.Gmos)
      _        <- st.ags.update(
                    _.focus(_.aoParked.value)
                      .replace(1.some)
                      .focus(_.hwParked.value)
                      .replace(0.some)
                      .focus(_.hwName.value)
                      .replace("IN".some)
                      .focus(_.sfParked.value)
                      .replace(1.some)
                  )
      _        <- ctr.lightPath(LightSource.Sky, LightSinkName.Ac)
      _        <- st.ags.update(
                    _.focus(_.aoParked.value)
                      .replace(0.some)
                      .focus(_.aoName.value)
                      .replace("IN".some)
                      .focus(_.hwParked.value)
                      .replace(0.some)
                      .focus(_.hwName.value)
                      .replace("IN".some)
                      .focus(_.sfParked.value)
                      .replace(0.some)
                      .focus(_.sfName.value)
                      .replace("ao2ac".some)
                  )
      _        <- ctr.lightPath(LightSource.AO, LightSinkName.Ac)
      r0       <- st.tcs.get
    } yield {
      assert(!r0.scienceFoldMech.position.connected)
      assert(!r0.scienceFoldMech.parkDir.connected)
      assert(!r0.hrwfsMech.position.connected)
      assert(!r0.hrwfsMech.parkDir.connected)
      assert(!r0.aoFoldMech.position.connected)
      assert(!r0.aoFoldMech.parkDir.connected)
    }
  }

  test("Apply acquisition offset") {
    val pOffset  = Angle.fromMicroarcseconds(4000000)
    val qOffset  = Angle.fromMicroarcseconds(-3000000)
    val expFrame = 2
    val expSize  = 5.0
    val expAngle = Angle.fromDoubleRadians(Math.atan2(4.0, 3.0)).toDoubleDegrees
    val expVt    = -(0x0002 | 0x0004 | 0x0008)

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- setWfsTrackingState(st.tcs, Focus[State](_.oiwfsTrackingState))
      _        <- ctr.enableGuide(oiGuideConfig)
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- ctr.acquisitionAdj(Offset(Offset.P(pOffset), Offset.Q(qOffset)), none, none)(
                    GuideConfig(oiGuideConfig, none)
                  )
      r1       <- st.tcs.get
    } yield {
      assert(r1.inPosition.connected)
      assert(r1.originAdjust.frame.connected)
      assert(r1.originAdjust.size.connected)
      assert(r1.originAdjust.angle.connected)
      assert(r1.originAdjust.vt.connected)
      r1.originAdjust.frame.value
        .flatMap(_.toIntOption)
        .map(x => assertEquals(x, expFrame))
        .getOrElse(fail("No value for parameter frame"))
      r1.originAdjust.size.value
        .flatMap(_.toDoubleOption)
        .map(x => assertEqualsDouble(x, expSize, 1e-6))
        .getOrElse(fail("No value for parameter size"))
      r1.originAdjust.angle.value
        .flatMap(_.toDoubleOption)
        .map(x => assertEqualsDouble(x, expAngle, 1e-6))
        .getOrElse(fail("No value for parameter angle"))
      r1.originAdjust.vt.value
        .flatMap(_.toIntOption)
        .map(x => assertEquals(x, expVt))
        .getOrElse(fail("No value for parameter vt"))
      checkGuide(r1, oiGuideConfig)
    }
  }

  private def testWfsSky(
    guideCfg: TelescopeGuideConfig,
    wfsStL:   Lens[State, ProbeTrackingStateState],
    cmdL:     Getter[TcsBaseController[IO], TimeSpan => GuideConfig => IO[ApplyCommandResult]],
    dfn:      Getter[StateRefs[IO], IO[TestChannel.State[String]]]
  ): IO[Unit] = {
    val expTime = TimeSpan.unsafeFromMicroseconds(50000)

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- setWfsTrackingState(st.tcs, wfsStL)
      _        <- ctr.enableGuide(guideCfg)
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- cmdL.get(ctr)(expTime)(GuideConfig(guideCfg, none))
      r1       <- st.tcs.get
      sdf      <- dfn.get(st)
    } yield {
      assert(sdf.connected)
      assertEquals(sdf.value, "20Hz.fits".some)

      checkGuide(r1, guideCfg)

      assert(r1.targetFilter.shortcircuit.connected)
      assertEquals(r1.targetFilter.shortcircuit.value, "Closed".some)
      assert(r1.targetAdjust.size.connected)
      r1.targetAdjust.size.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No size value set"))(v => assertEqualsDouble(v, -60.0, 1e-6))
      assert(r1.targetAdjust.angle.connected)
      r1.targetAdjust.angle.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No angle value set"))(v => assertEqualsDouble(v, 0.0, 1e-6))
    }
  }

  test("Take PWFS1 sky") {
    testWfsSky(
      p1GuideConfig,
      Focus[State](_.pwfs1TrackingState),
      Getter[TcsBaseController[IO], TimeSpan => GuideConfig => IO[ApplyCommandResult]](_.pwfs1Sky),
      Getter[StateRefs[IO], IO[TestChannel.State[String]]](_.tcs.get.map(_.pwfs1.dark))
    )
  }

  test("Take PWFS2 sky") {
    testWfsSky(
      p2GuideConfig,
      Focus[State](_.pwfs2TrackingState),
      Getter[TcsBaseController[IO], TimeSpan => GuideConfig => IO[ApplyCommandResult]](_.pwfs2Sky),
      Getter[StateRefs[IO], IO[TestChannel.State[String]]](_.tcs.get.map(_.pwfs2.dark))
    )
  }

  test("Take OIWFS sky") {
    testWfsSky(
      oiGuideConfig,
      Focus[State](_.oiwfsTrackingState),
      Getter[TcsBaseController[IO], TimeSpan => GuideConfig => IO[ApplyCommandResult]](_.oiwfsSky),
      Getter[StateRefs[IO], IO[TestChannel.State[String]]](_.oi.get.map(_.seqDarkFilename))
    )
  }

  test("Apply target correction") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- setWfsTrackingState(st.tcs, Focus[State](_.oiwfsTrackingState))
      _        <- ctr.enableGuide(oiGuideConfig)
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- ctr.targetAdjust(VirtualTelescope.SourceA,
                                   HandsetAdjustment.EquatorialAdjustment(Angle.fromDoubleArcseconds(-8.0),
                                                                          Angle.fromDoubleArcseconds(6.0)
                                   ),
                                   true
                  )(GuideConfig(oiGuideConfig, none))
      r1       <- st.tcs.get
      _        <- ctr.targetAdjust(
                    VirtualTelescope.Oiwfs,
                    HandsetAdjustment.InstrumentAdjustment(
                      Offset(Offset.P(Angle.fromDoubleArcseconds(-3.0)),
                             Offset.Q(Angle.fromDoubleArcseconds(3.0))
                      )
                    ),
                    true
                  )(GuideConfig(oiGuideConfig, none))
      r2       <- st.tcs.get
    } yield {
      checkGuide(r1, oiGuideConfig)

      assert(r1.targetAdjust.frame.connected)
      assert(r1.targetAdjust.size.connected)
      assert(r1.targetAdjust.angle.connected)
      assert(r1.targetAdjust.vt.connected)
      assertEquals(r1.targetAdjust.frame.value.flatMap(_.toIntOption), 3.some)
      r1.targetAdjust.size.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No size value set"))(v => assertEqualsDouble(v, 10.0, 1e-6))
      r1.targetAdjust.angle.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No angle value set"))(v =>
          assertEqualsDouble(normalizeAnglePosDegree(v),
                             normalizeAnglePosDegree(Math.toDegrees(Math.atan2(-8.0, 6.0))),
                             1e-6
          )
        )
      assertEquals(r1.targetAdjust.vt.value.flatMap(_.toIntOption), -2.some)
      assertEquals(r2.targetAdjust.frame.value.flatMap(_.toIntOption), 2.some)
      r2.targetAdjust.size.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No size value set"))(v => assertEqualsDouble(v, 3.0 * Math.sqrt(2.0), 1e-6))
      r2.targetAdjust.angle.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No angle value set"))(v => assertEqualsDouble(v, 225.0, 1e-6))
      assertEquals(r2.targetAdjust.vt.value.flatMap(_.toIntOption), -64.some)
    }
  }

  test("Apply origin correction") {
    val guideCfg = TelescopeGuideConfig(
      mountGuide = MountGuideOption.MountGuideOn,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      m2Guide = M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
      dayTimeMode = Some(false),
      probeGuide = none
    )

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- setWfsTrackingState(st.tcs, Focus[State](_.oiwfsTrackingState))
      _        <- ctr.enableGuide(guideCfg)
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- ctr.originAdjust(HandsetAdjustment.EquatorialAdjustment(Angle.fromDoubleArcseconds(-8.0),
                                                                          Angle.fromDoubleArcseconds(6.0)
                                   ),
                                   true
                  )(GuideConfig(guideCfg, none))
      r1       <- st.tcs.get
      _        <- ctr.originAdjust(HandsetAdjustment.InstrumentAdjustment(
                                     Offset(Offset.P(Angle.fromDoubleArcseconds(-3.0)),
                                            Offset.Q(Angle.fromDoubleArcseconds(3.0))
                                     )
                                   ),
                                   true
                  )(GuideConfig(guideCfg, none))
      r2       <- st.tcs.get
    } yield {
      assert(r1.m1Guide.connected)
      assert(r1.m1GuideConfig.source.connected)
      assert(r1.m2Guide.connected)
      assert(r1.m2GuideConfig.source.connected)
      assert(r1.m2GuideConfig.beam.connected)
      assert(r1.m2GuideMode.connected)

      assertEquals(r1.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m1GuideConfig.source.value, M1Source.OIWFS.tag.toUpperCase.some)
      assertEquals(r1.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m2GuideConfig.source.value, TipTiltSource.OIWFS.tag.toUpperCase.some)
      assertEquals(r1.m2GuideConfig.beam.value, "A".some)
      assertEquals(r1.m2GuideMode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )

      assert(r1.originAdjust.frame.connected)
      assert(r1.originAdjust.size.connected)
      assert(r1.originAdjust.angle.connected)
      assert(r1.originAdjust.vt.connected)
      assertEquals(r1.originAdjust.frame.value.flatMap(_.toIntOption), 3.some)
      r1.originAdjust.size.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No size value set"))(v => assertEqualsDouble(v, 10.0, 1e-6))
      r1.originAdjust.angle.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No angle value set"))(v =>
          assertEqualsDouble(normalizeAnglePosDegree(v),
                             normalizeAnglePosDegree(Math.toDegrees(Math.atan2(-8.0, 6.0))),
                             1e-6
          )
        )
      assertEquals(r1.originAdjust.vt.value.flatMap(_.toIntOption), -2.some)
      assertEquals(r2.originAdjust.frame.value.flatMap(_.toIntOption), 2.some)
      r2.originAdjust.size.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No size value set"))(v => assertEqualsDouble(v, 3.0 * Math.sqrt(2.0), 1e-6))
      r2.originAdjust.angle.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No angle value set"))(v => assertEqualsDouble(v, 225.0, 1e-6))
      assertEquals(r2.originAdjust.vt.value.flatMap(_.toIntOption), -2.some)
    }
  }

  test("Apply pointing correction") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- ctr.pointingAdjust(
                    HandsetAdjustment.EquatorialAdjustment(Angle.fromDoubleArcseconds(-8.0),
                                                           Angle.fromDoubleArcseconds(6.0)
                    )
                  )
      r1       <- st.tcs.get
      _        <- ctr.pointingAdjust(
                    HandsetAdjustment.InstrumentAdjustment(
                      Offset(Offset.P(Angle.fromDoubleArcseconds(-3.0)),
                             Offset.Q(Angle.fromDoubleArcseconds(3.0))
                      )
                    )
                  )
      r2       <- st.tcs.get
      _        <- ctr.pointingAdjust(
                    HandsetAdjustment.HorizontalAdjustment(Angle.fromDoubleArcseconds(-3.0),
                                                           Angle.fromDoubleArcseconds(4.0)
                    )
                  )
      r3       <- st.tcs.get
      _        <- ctr.pointingAdjust(
                    HandsetAdjustment.FocalPlaneAdjustment(
                      FocalPlaneOffset(DeltaX(Angle.fromDoubleArcseconds(3.0)),
                                       DeltaY(Angle.fromDoubleArcseconds(4.0))
                      )
                    )
                  )
      r4       <- st.tcs.get
    } yield {
      assert(r1.pointingAdjust.frame.connected)
      assert(r1.pointingAdjust.size.connected)
      assert(r1.pointingAdjust.angle.connected)
      // Equatorial coordinates (RA, Dec)
      assertEquals(r1.pointingAdjust.frame.value.flatMap(_.toIntOption), 3.some)
      r1.pointingAdjust.size.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No size value set"))(v => assertEqualsDouble(v, 10.0, 1e-6))
      r1.pointingAdjust.angle.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No angle value set"))(v =>
          assertEqualsDouble(normalizeAnglePosDegree(v),
                             normalizeAnglePosDegree(Math.toDegrees(Math.atan2(-8.0, 6.0))),
                             1e-6
          )
        )
      // Instrument coordinates (P, Q)
      assertEquals(r2.pointingAdjust.frame.value.flatMap(_.toIntOption), 2.some)
      r2.pointingAdjust.size.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No size value set"))(v => assertEqualsDouble(v, 3.0 * Math.sqrt(2.0), 1e-6))
      r2.pointingAdjust.angle.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No angle value set"))(v => assertEqualsDouble(v, 225.0, 1e-6))
      // Horizontal Coordinates (Az, El)
      assertEquals(r3.pointingAdjust.frame.value.flatMap(_.toIntOption), 0.some)
      r3.pointingAdjust.size.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No size value set"))(v => assertEqualsDouble(v, 5.0, 1e-6))
      r3.pointingAdjust.angle.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No angle value set"))(v =>
          assertEqualsDouble(v,
                             normalizeAnglePosDegree(Math.toDegrees(Math.atan2(-3.0, 4.0))),
                             1e-6
          )
        )
      // Focal Plane coordinates (x, y)
      assertEquals(r4.pointingAdjust.frame.value.flatMap(_.toIntOption), 1.some)
      r4.pointingAdjust.size.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No size value set"))(v => assertEqualsDouble(v, 5.0, 1e-6))
      r4.pointingAdjust.angle.value
        .flatMap(_.toDoubleOption)
        .fold(fail("No angle value set"))(v =>
          assertEqualsDouble(v,
                             normalizeAnglePosDegree(Math.toDegrees(Math.atan2(-3.0, 4.0))),
                             1e-6
          )
        )
    }
  }

  test("Absorb target correction") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- ctr.targetOffsetAbsorb(VirtualTelescope.SourceA)
      r1       <- st.tcs.get
      _        <- ctr.targetOffsetAbsorb(VirtualTelescope.Oiwfs)
      r2       <- st.tcs.get
    } yield {
      assert(r1.targetOffsetAbsorb.vt.connected)
      assert(r1.targetOffsetAbsorb.index.connected)
      assertEquals(r1.targetOffsetAbsorb.vt.value, "SOURCE A".some)
      assertEquals(r1.targetOffsetAbsorb.index.value, "all".some)
      assertEquals(r2.targetOffsetAbsorb.vt.value, "OIWFS".some)
      assertEquals(r2.targetOffsetAbsorb.index.value, "0".some)
    }
  }

  test("Clear target correction") {
    val guideCfg = TelescopeGuideConfig(
      mountGuide = MountGuideOption.MountGuideOn,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      m2Guide = M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
      dayTimeMode = Some(false),
      probeGuide = none
    )

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- setWfsTrackingState(st.tcs, Focus[State](_.oiwfsTrackingState))
      _        <- ctr.enableGuide(guideCfg)
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- ctr.targetOffsetClear(VirtualTelescope.SourceA, true)(GuideConfig(guideCfg, none))
      r1       <- st.tcs.get
    } yield {
      assert(r1.m1Guide.connected)
      assert(r1.m1GuideConfig.source.connected)
      assert(r1.m2Guide.connected)
      assert(r1.m2GuideConfig.source.connected)
      assert(r1.m2GuideConfig.beam.connected)
      assert(r1.m2GuideMode.connected)

      assertEquals(r1.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m1GuideConfig.source.value, M1Source.OIWFS.tag.toUpperCase.some)
      assertEquals(r1.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m2GuideConfig.source.value, TipTiltSource.OIWFS.tag.toUpperCase.some)
      assertEquals(r1.m2GuideConfig.beam.value, "A".some)
      assertEquals(r1.m2GuideMode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )

      assert(r1.targetOffsetClear.vt.connected)
      assert(r1.targetOffsetClear.index.connected)
      assertEquals(r1.targetOffsetClear.vt.value, "SOURCE A".some)
      assertEquals(r1.targetOffsetClear.index.value, "all".some)
    }
  }

  test("Absorb origin correction") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- ctr.originOffsetAbsorb
      r1       <- st.tcs.get
    } yield {
      assert(r1.originOffsetAbsorb.vt.connected)
      assert(r1.originOffsetAbsorb.index.connected)
      assertEquals(r1.originOffsetAbsorb.vt.value, "SOURCE C".some)
      assertEquals(r1.originOffsetAbsorb.index.value, "all".some)
    }
  }

  test("Clear origin correction") {
    val guideCfg = TelescopeGuideConfig(
      mountGuide = MountGuideOption.MountGuideOn,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      m2Guide = M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
      dayTimeMode = Some(false),
      probeGuide = none
    )

    for {
      x        <- createController()
      (st, ctr) = x
      _        <- setWfsTrackingState(st.tcs, Focus[State](_.oiwfsTrackingState))
      _        <- ctr.enableGuide(guideCfg)
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- ctr.originOffsetClear(true)(GuideConfig(guideCfg, none))
      r1       <- st.tcs.get
    } yield {
      assert(r1.m1Guide.connected)
      assert(r1.m1GuideConfig.source.connected)
      assert(r1.m2Guide.connected)
      assert(r1.m2GuideConfig.source.connected)
      assert(r1.m2GuideConfig.beam.connected)
      assert(r1.m2GuideMode.connected)

      assertEquals(r1.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m1GuideConfig.source.value, M1Source.OIWFS.tag.toUpperCase.some)
      assertEquals(r1.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m2GuideConfig.source.value, TipTiltSource.OIWFS.tag.toUpperCase.some)
      assertEquals(r1.m2GuideConfig.beam.value, "A".some)
      assertEquals(r1.m2GuideMode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )

      assert(r1.originOffsetClear.vt.connected)
      assert(r1.originOffsetClear.index.connected)
      assertEquals(r1.originOffsetClear.vt.value, "SOURCE C".some)
      assertEquals(r1.originOffsetClear.index.value, "all".some)
    }
  }

  test("Clear local pointing correction") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- ctr.pointingOffsetClearLocal
      r1       <- st.tcs.get
    } yield {
      assert(r1.pointingConfig.name.connected)
      assert(r1.pointingConfig.level.connected)
      assert(r1.pointingConfig.value.connected)
      assertEquals(r1.pointingConfig.name.value, "CE".some)
      assertEquals(r1.pointingConfig.level.value, "Local".some)
      assertEquals(r1.pointingConfig.value.value, "0.0".some)
    }
  }

  test("Clear guide pointing correction") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- ctr.pointingOffsetClearGuide
      r1       <- st.tcs.get
    } yield {
      assert(r1.zeroGuideDirState.connected)
      assertEquals(r1.zeroGuideDirState.value, CadDirective.MARK.some)
    }
  }

  test("Absorb guide pointing correction") {
    for {
      x        <- createController()
      (st, ctr) = x
      _        <- st.tcs.update(_.focus(_.inPosition.value).replace("TRUE".some))
      _        <- ctr.pointingOffsetAbsorbGuide
      r1       <- st.tcs.get
    } yield {
      assert(r1.absorbGuideDirState.connected)
      assertEquals(r1.absorbGuideDirState.value, CadDirective.MARK.some)
    }
  }

  case class StateRefs[F[_]](
    tcs:  Ref[F, TestTcsEpicsSystem.State],
    p1:   Ref[F, TestWfsEpicsSystem.State],
    p2:   Ref[F, TestWfsEpicsSystem.State],
    oiw:  Ref[F, TestWfsEpicsSystem.State],
    oi:   Ref[F, TestOiwfsEpicsSystem.State],
    mcs:  Ref[F, TestMcsEpicsSystem.State],
    scs:  Ref[F, TestScsEpicsSystem.State],
    crcs: Ref[F, TestCrcsEpicsSystem.State],
    ags:  Ref[F, TestAgsEpicsSystem.State],
    ac:   Ref[F, TestAcquisitionCameraEpicsSystem.State]
  )

  def createController(site: Site = Site.GS): IO[(StateRefs[IO], TcsBaseControllerEpics[IO])] =
    Dispatcher.parallel[IO](true).use { dispatcher =>
      given Dispatcher[IO] = dispatcher
      for {
        tcs  <- Ref.of[IO, TestTcsEpicsSystem.State](TestTcsEpicsSystem.defaultState)
        p1   <- Ref.of[IO, TestWfsEpicsSystem.State](TestWfsEpicsSystem.defaultState)
        p2   <- Ref.of[IO, TestWfsEpicsSystem.State](TestWfsEpicsSystem.defaultState)
        oiw  <- Ref.of[IO, TestWfsEpicsSystem.State](TestWfsEpicsSystem.defaultState)
        oi   <- Ref.of[IO, TestOiwfsEpicsSystem.State](TestOiwfsEpicsSystem.defaultState)
        mcs  <- Ref.of[IO, TestMcsEpicsSystem.State](TestMcsEpicsSystem.defaultState)
        scs  <- Ref.of[IO, TestScsEpicsSystem.State](TestScsEpicsSystem.defaultState)
        crcs <- Ref.of[IO, TestCrcsEpicsSystem.State](TestCrcsEpicsSystem.defaultState)
        ags  <- Ref.of[IO, TestAgsEpicsSystem.State](TestAgsEpicsSystem.defaultState)
        st   <- Ref.of[IO, TcsBaseControllerEpics.State](TcsBaseControllerEpics.State.default)
        ac   <- Ref.of[IO, TestAcquisitionCameraEpicsSystem.State](
                  TestAcquisitionCameraEpicsSystem.defaultState
                )
      } yield (
        StateRefs(tcs, p1, p2, oiw, oi, mcs, scs, crcs, ags, ac),
        (site === Site.GS).fold(
          new TcsSouthControllerEpics[IO](
            EpicsSystems(
              TestTcsEpicsSystem.build(tcs),
              TestWfsEpicsSystem.build("PWFS1", p1),
              TestWfsEpicsSystem.build("PWFS2", p2),
              TestOiwfsEpicsSystem.build(oiw, oi),
              TestMcsEpicsSystem.build(mcs),
              TestScsEpicsSystem.build(scs),
              TestCrcsEpicsSystem.build(crcs),
              TestAgsEpicsSystem.build(ags),
              TestAcquisitionCameraEpicsSystem.build(ac)
            ),
            DefaultTimeout,
            st
          ),
          new TcsNorthControllerEpics[IO](
            EpicsSystems(
              TestTcsEpicsSystem.build(tcs),
              TestWfsEpicsSystem.build("PWFS1", p1),
              TestWfsEpicsSystem.build("PWFS2", p2),
              TestOiwfsEpicsSystem.build(oiw, oi),
              TestMcsEpicsSystem.build(mcs),
              TestScsEpicsSystem.build(scs),
              TestCrcsEpicsSystem.build(crcs),
              TestAgsEpicsSystem.build(ags),
              TestAcquisitionCameraEpicsSystem.build(ac)
            ),
            DefaultTimeout,
            st
          )
        )
      )
    }

  def normalizeAnglePosDegree(a: Double): Double =
    if (a < 0.0) a + 360.0
    else if (a >= 360.0) a - 360.0
    else a

}
