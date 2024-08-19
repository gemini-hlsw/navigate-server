// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all.*
import lucuma.core.enums.ComaOption
import lucuma.core.enums.GuideProbe
import lucuma.core.enums.Instrument
import lucuma.core.enums.M1Source
import lucuma.core.enums.MountGuideOption
import lucuma.core.enums.TipTiltSource
import lucuma.core.math.Angle
import lucuma.core.math.Coordinates
import lucuma.core.math.Epoch
import lucuma.core.math.HourAngle
import lucuma.core.math.Wavelength
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.M2GuideConfig.M2GuideOn
import lucuma.core.model.ProbeGuide
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.Enumerated
import lucuma.core.util.TimeSpan
import monocle.syntax.all.*
import mouse.boolean.given
import munit.CatsEffectSuite
import navigate.epics.TestChannel
import navigate.model.Distance
import navigate.model.enums.CentralBafflePosition
import navigate.model.enums.DeployableBafflePosition
import navigate.model.enums.DomeMode
import navigate.model.enums.ShutterMode
import navigate.server.acm.CadDirective
import navigate.server.epicsdata
import navigate.server.epicsdata.BinaryOnOff
import navigate.server.epicsdata.BinaryYesNo
import navigate.server.tcs.FollowStatus.Following
import navigate.server.tcs.FollowStatus.NotFollowing
import navigate.server.tcs.ParkStatus.NotParked
import navigate.server.tcs.ParkStatus.Parked

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import Target.SiderealTarget
import TcsBaseController.*
import TestTcsEpicsSystem.GuideConfigState

class TcsBaseControllerEpicsSuite extends CatsEffectSuite {

  private val DefaultTimeout: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS)

  private val Tolerance: Double = 1e-6
  private def compareDouble(a: Double, b: Double): Boolean = Math.abs(a - b) < Tolerance

  test("Mount commands") {
    for {
      x        <- createController
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

  test("Rotator commands") {
    val testAngle = Angle.fromDoubleDegrees(123.456)

    for {
      x        <- createController
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
      x        <- createController
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
    val target = SiderealTarget(
      objectName = "dummy",
      wavelength = Wavelength.fromIntPicometers(400 * 1000),
      coordinates = Coordinates.unsafeFromRadians(-0.321, 0.123),
      epoch = Epoch.J2000,
      properMotion = none,
      radialVelocity = none,
      parallax = none
    )

    val oiwfsTarget = SiderealTarget(
      objectName = "oiwfsDummy",
      wavelength = Wavelength.fromIntPicometers(600 * 1000),
      coordinates = Coordinates.unsafeFromRadians(-0.123, 0.321),
      epoch = Epoch.J2000,
      properMotion = none,
      radialVelocity = none,
      parallax = none
    )

    val oiwfsTracking = TrackingConfig(true, false, false, true)

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
      origin = Origin(Distance.fromLongMicrometers(4567), Distance.fromLongMicrometers(-8901))
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.slew(
                    slewOptions,
                    TcsConfig(
                      target,
                      instrumentSpecifics,
                      GuiderConfig(oiwfsTarget, oiwfsTracking).some,
                      RotatorTrackConfig(Angle.Angle90, RotatorTrackingMode.Tracking),
                      Instrument.GmosNorth
                    )
                  )
      rs       <- st.tcs.get
    } yield {
      // Base Target
      assert(rs.sourceA.objectName.connected)
      assert(rs.sourceA.brightness.connected)
      assert(rs.sourceA.coord1.connected)
      assert(rs.sourceA.coord2.connected)
      assert(rs.sourceA.properMotion1.connected)
      assert(rs.sourceA.properMotion2.connected)
      assert(rs.sourceA.epoch.connected)
      assert(rs.sourceA.equinox.connected)
      assert(rs.sourceA.parallax.connected)
      assert(rs.sourceA.radialVelocity.connected)
      assert(rs.sourceA.coordSystem.connected)
      assert(rs.sourceA.ephemerisFile.connected)
      assertEquals(rs.sourceA.objectName.value, target.objectName.some)
      assertEquals(
        rs.sourceA.coord1.value.flatMap(HourAngle.fromStringHMS.getOption),
        Some(target.coordinates.ra.toHourAngle)
      )
      assertEquals(
        rs.sourceA.coord2.value.flatMap(Angle.fromStringSignedDMS.getOption),
        Some(target.coordinates.dec.toAngle)
      )
      assert(rs.sourceA.properMotion1.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.sourceA.properMotion2.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.sourceA.epoch.value.exists(x => compareDouble(x.toDouble, target.epoch.epochYear)))
      assert(rs.sourceA.parallax.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.sourceA.radialVelocity.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assertEquals(rs.sourceA.coordSystem.value, SystemDefault.some)
      assertEquals(rs.sourceA.ephemerisFile.value, "".some)

      // OIWFS Target
      assert(rs.oiwfsTarget.objectName.connected)
      assert(rs.oiwfsTarget.brightness.connected)
      assert(rs.oiwfsTarget.coord1.connected)
      assert(rs.oiwfsTarget.coord2.connected)
      assert(rs.oiwfsTarget.properMotion1.connected)
      assert(rs.oiwfsTarget.properMotion2.connected)
      assert(rs.oiwfsTarget.epoch.connected)
      assert(rs.oiwfsTarget.equinox.connected)
      assert(rs.oiwfsTarget.parallax.connected)
      assert(rs.oiwfsTarget.radialVelocity.connected)
      assert(rs.oiwfsTarget.coordSystem.connected)
      assert(rs.oiwfsTarget.ephemerisFile.connected)
      assertEquals(rs.oiwfsTarget.objectName.value, oiwfsTarget.objectName.some)
      assertEquals(
        rs.oiwfsTarget.coord1.value.flatMap(HourAngle.fromStringHMS.getOption),
        Some(oiwfsTarget.coordinates.ra.toHourAngle)
      )
      assertEquals(
        rs.oiwfsTarget.coord2.value.flatMap(Angle.fromStringSignedDMS.getOption),
        Some(oiwfsTarget.coordinates.dec.toAngle)
      )
      assert(rs.oiwfsTarget.properMotion1.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfsTarget.properMotion2.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(
        rs.oiwfsTarget.epoch.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.epoch.epochYear)
        )
      )
      assert(rs.oiwfsTarget.parallax.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfsTarget.radialVelocity.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assertEquals(rs.oiwfsTarget.coordSystem.value, SystemDefault.some)
      assertEquals(rs.oiwfsTarget.ephemerisFile.value, "".some)

      // OIWFS probe tracking
      assert(rs.oiwfsTracking.nodAchopA.connected)
      assert(rs.oiwfsTracking.nodAchopB.connected)
      assert(rs.oiwfsTracking.nodBchopA.connected)
      assert(rs.oiwfsTracking.nodBchopB.connected)
      assertEquals(rs.oiwfsTracking.nodAchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   oiwfsTracking.nodAchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodAchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   oiwfsTracking.nodAchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   oiwfsTracking.nodBchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   oiwfsTracking.nodBchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
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
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xb.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xc.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.ya.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.yb.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.yc.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
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
      origin = Origin(Distance.fromLongMicrometers(4567), Distance.fromLongMicrometers(-8901))
    )

    for {
      x        <- createController
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
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xb.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xc.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.ya.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.yb.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.yc.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
        )
      )
    }
  }

  test("oiwfsTarget command") {
    val oiwfsTarget = SiderealTarget(
      objectName = "oiwfsDummy",
      wavelength = Wavelength.fromIntPicometers(600 * 1000),
      coordinates = Coordinates.unsafeFromRadians(-0.123, 0.321),
      epoch = Epoch.J2000,
      properMotion = none,
      radialVelocity = none,
      parallax = none
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.oiwfsTarget(oiwfsTarget)
      rs       <- st.tcs.get
    } yield {
      assert(rs.oiwfsTarget.objectName.connected)
      assert(rs.oiwfsTarget.brightness.connected)
      assert(rs.oiwfsTarget.coord1.connected)
      assert(rs.oiwfsTarget.coord2.connected)
      assert(rs.oiwfsTarget.properMotion1.connected)
      assert(rs.oiwfsTarget.properMotion2.connected)
      assert(rs.oiwfsTarget.epoch.connected)
      assert(rs.oiwfsTarget.equinox.connected)
      assert(rs.oiwfsTarget.parallax.connected)
      assert(rs.oiwfsTarget.radialVelocity.connected)
      assert(rs.oiwfsTarget.coordSystem.connected)
      assert(rs.oiwfsTarget.ephemerisFile.connected)
      assertEquals(rs.oiwfsTarget.objectName.value, oiwfsTarget.objectName.some)
      assertEquals(
        rs.oiwfsTarget.coord1.value.flatMap(HourAngle.fromStringHMS.getOption),
        Some(oiwfsTarget.coordinates.ra.toHourAngle)
      )
      assertEquals(
        rs.oiwfsTarget.coord2.value.flatMap(Angle.fromStringSignedDMS.getOption),
        Some(oiwfsTarget.coordinates.dec.toAngle)
      )
      assert(rs.oiwfsTarget.properMotion1.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfsTarget.properMotion2.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(
        rs.oiwfsTarget.epoch.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.epoch.epochYear)
        )
      )
      assert(rs.oiwfsTarget.parallax.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfsTarget.radialVelocity.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assertEquals(rs.oiwfsTarget.coordSystem.value, SystemDefault.some)
      assertEquals(rs.oiwfsTarget.ephemerisFile.value, "".some)
    }
  }

  test("oiwfs probe tracking command") {
    val trackingConfig = TrackingConfig(true, false, false, true)

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.oiwfsProbeTracking(trackingConfig)
      rs       <- st.tcs.get
    } yield {
      assert(rs.oiwfsTracking.nodAchopA.connected)
      assert(rs.oiwfsTracking.nodAchopB.connected)
      assert(rs.oiwfsTracking.nodBchopA.connected)
      assert(rs.oiwfsTracking.nodBchopB.connected)
      assertEquals(rs.oiwfsTracking.nodAchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodAchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodAchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodAchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodBchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodBchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
    }

  }

  test("oiwfs probe park command") {
    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.oiwfsPark
      rs       <- st.tcs.get
    } yield {
      assert(rs.oiwfsProbe.parkDir.connected)
      assertEquals(rs.oiwfsProbe.parkDir.value, CadDirective.MARK.some)
    }
  }

  test("oiwfs probe follow command") {
    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.oiwfsFollow(true)
      r1       <- st.tcs.get
      _        <- ctr.oiwfsFollow(false)
      r2       <- st.tcs.get
    } yield {
      assert(r1.oiwfsProbe.follow.connected)
      assertEquals(r1.oiwfsProbe.follow.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r2.oiwfsProbe.follow.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
    }
  }

  test("Enable and disable guiding default gains") {
    val guideCfg = TelescopeGuideConfig(
      mountGuide = MountGuideOption.MountGuideOn,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      m2Guide = M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
      dayTimeMode = Some(false),
      probeGuide = none
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.enableGuide(guideCfg)
      r1       <- st.tcs.get
      p1_1     <- st.p1.get
      p2_1     <- st.p2.get
      oi_1     <- st.oi.get
      _        <- ctr.disableGuide
      r2       <- st.tcs.get
    } yield {
      assert(r1.m1Guide.connected)
      assert(r1.m1GuideConfig.source.connected)
      assert(r1.m1GuideConfig.frames.connected)
      assert(r1.m1GuideConfig.weighting.connected)
      assert(r1.m1GuideConfig.filename.connected)
      assert(r1.m2Guide.connected)
      assert(r1.m2GuideConfig.source.connected)
      assert(r1.m2GuideConfig.beam.connected)
      assert(r1.m2GuideConfig.filter.connected)
      assert(r1.m2GuideConfig.samplefreq.connected)
      assert(r1.m2GuideConfig.reset.connected)
      assert(r1.m2GuideMode.connected)
      assert(r1.m2GuideReset.connected)
      assert(r1.mountGuide.mode.connected)
      assert(r1.mountGuide.source.connected)
      assert(p1_1.reset.connected)
      assert(p2_1.reset.connected)
      assert(oi_1.reset.connected)

      assertEquals(r1.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m1GuideConfig.source.value.flatMap(Enumerated[M1Source].fromTag),
                   M1Source.OIWFS.some
      )
      assertEquals(r1.m1GuideConfig.frames.value.flatMap(_.toIntOption), 1.some)
      assertEquals(r1.m1GuideConfig.weighting.value, "none".some)
      assertEquals(r1.m1GuideConfig.filename.value, "".some)
      assertEquals(r1.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m2GuideConfig.source.value.flatMap(Enumerated[TipTiltSource].fromTag),
                   TipTiltSource.OIWFS.some
      )
      assertEquals(r1.m2GuideConfig.beam.value, "B".some)
      assertEquals(r1.m2GuideConfig.filter.value, "raw".some)
      assertEquals(r1.m2GuideConfig.samplefreq.value.flatMap(_.toDoubleOption), 200.0.some)
      assertEquals(r1.m2GuideConfig.reset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(r1.m2GuideMode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.m2GuideReset.value, CadDirective.MARK.some)
      assertEquals(r1.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.mountGuide.source.value, "SCS".some)

      assertEquals(r2.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(p1_1.reset.value, 1.0.some)

      assertEquals(p2_1.reset.value, 1.0.some)

      assertEquals(oi_1.reset.value, 1.0.some)
    }
  }

  test("Enable and disable guiding day mode") {
    val guideCfg = TelescopeGuideConfig(
      mountGuide = MountGuideOption.MountGuideOn,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      m2Guide = M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
      dayTimeMode = Some(true),
      probeGuide = none
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.enableGuide(guideCfg)
      r1       <- st.tcs.get
      p1_1     <- st.p1.get
      p2_1     <- st.p2.get
      oi_1     <- st.oi.get
      _        <- ctr.disableGuide
      r2       <- st.tcs.get
    } yield {
      assert(r1.m1Guide.connected)
      assert(r1.m1GuideConfig.source.connected)
      assert(r1.m1GuideConfig.frames.connected)
      assert(r1.m1GuideConfig.weighting.connected)
      assert(r1.m1GuideConfig.filename.connected)
      assert(r1.m2Guide.connected)
      assert(r1.m2GuideConfig.source.connected)
      assert(r1.m2GuideConfig.beam.connected)
      assert(r1.m2GuideConfig.filter.connected)
      assert(r1.m2GuideConfig.samplefreq.connected)
      assert(r1.m2GuideConfig.reset.connected)
      assert(r1.m2GuideMode.connected)
      assert(r1.m2GuideReset.connected)
      assert(r1.mountGuide.mode.connected)
      assert(r1.mountGuide.source.connected)
      assert(p1_1.tipGain.connected)
      assert(p1_1.tiltGain.connected)
      assert(p1_1.focusGain.connected)
      assert(p2_1.tipGain.connected)
      assert(p2_1.tiltGain.connected)
      assert(p2_1.focusGain.connected)
      assert(oi_1.tipGain.connected)
      assert(oi_1.tiltGain.connected)
      assert(oi_1.focusGain.connected)

      assertEquals(r1.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m1GuideConfig.source.value.flatMap(Enumerated[M1Source].fromTag),
                   M1Source.OIWFS.some
      )
      assertEquals(r1.m1GuideConfig.frames.value.flatMap(_.toIntOption), 1.some)
      assertEquals(r1.m1GuideConfig.weighting.value, "none".some)
      assertEquals(r1.m1GuideConfig.filename.value, "".some)
      assertEquals(r1.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m2GuideConfig.source.value.flatMap(Enumerated[TipTiltSource].fromTag),
                   TipTiltSource.OIWFS.some
      )
      assertEquals(r1.m2GuideConfig.beam.value, "B".some)
      assertEquals(r1.m2GuideConfig.filter.value, "raw".some)
      assertEquals(r1.m2GuideConfig.samplefreq.value.flatMap(_.toDoubleOption), 200.0.some)
      assertEquals(r1.m2GuideConfig.reset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(r1.m2GuideMode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.m2GuideReset.value, CadDirective.MARK.some)
      assertEquals(r1.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.mountGuide.source.value, "SCS".some)

      assertEquals(r2.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
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
    val guideCfg = TelescopeGuideConfig(
      mountGuide = MountGuideOption.MountGuideOn,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      m2Guide = M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
      dayTimeMode = Some(false),
      probeGuide = none
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.enableGuide(guideCfg)
      r1       <- st.tcs.get
      _        <- ctr.disableGuide
      r2       <- st.tcs.get
    } yield {
      assert(r1.m1Guide.connected)
      assert(r1.m1GuideConfig.source.connected)
      assert(r1.m1GuideConfig.frames.connected)
      assert(r1.m1GuideConfig.weighting.connected)
      assert(r1.m1GuideConfig.filename.connected)
      assert(r1.m2Guide.connected)
      assert(r1.m2GuideConfig.source.connected)
      assert(r1.m2GuideConfig.beam.connected)
      assert(r1.m2GuideConfig.filter.connected)
      assert(r1.m2GuideConfig.samplefreq.connected)
      assert(r1.m2GuideConfig.reset.connected)
      assert(r1.m2GuideMode.connected)
      assert(r1.m2GuideReset.connected)
      assert(r1.mountGuide.mode.connected)
      assert(r1.mountGuide.source.connected)
      assert(r1.probeGuideMode.state.connected)

      assertEquals(r1.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m1GuideConfig.source.value.flatMap(Enumerated[M1Source].fromTag),
                   M1Source.OIWFS.some
      )
      assertEquals(r1.m1GuideConfig.frames.value.flatMap(_.toIntOption), 1.some)
      assertEquals(r1.m1GuideConfig.weighting.value, "none".some)
      assertEquals(r1.m1GuideConfig.filename.value, "".some)
      assertEquals(r1.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m2GuideConfig.source.value.flatMap(Enumerated[TipTiltSource].fromTag),
                   TipTiltSource.OIWFS.some
      )
      assertEquals(r1.m2GuideConfig.beam.value, "B".some)
      assertEquals(r1.m2GuideConfig.filter.value, "raw".some)
      assertEquals(r1.m2GuideConfig.samplefreq.value.flatMap(_.toDoubleOption), 200.0.some)
      assertEquals(r1.m2GuideConfig.reset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(r1.m2GuideMode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.m2GuideReset.value, CadDirective.MARK.some)
      assertEquals(r1.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.mountGuide.source.value, "SCS".some)

      assertEquals(r2.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(r1.probeGuideMode.state.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
    }
  }

  test("Set guide mode OIWFS to OIWFS") {
    val guideCfg = TelescopeGuideConfig(
      mountGuide = MountGuideOption.MountGuideOn,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      m2Guide = M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
      dayTimeMode = Some(false),
      probeGuide = ProbeGuide(GuideProbe.GmosOIWFS, GuideProbe.GmosOIWFS).some
    )

    for {
      x        <- createController
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
    val guideCfg = TelescopeGuideConfig(
      mountGuide = MountGuideOption.MountGuideOn,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      m2Guide = M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
      dayTimeMode = Some(false),
      probeGuide = ProbeGuide(GuideProbe.PWFS1, GuideProbe.PWFS2).some
    )

    for {
      x        <- createController
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

  test("Start OIWFS exposures") {
    val testVal = TimeSpan.unsafeFromMicroseconds(12345)

    for {
      x        <- createController
      (st, ctr) = x
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(defaultGuideState))
      _        <- ctr.oiwfsObserve(testVal)
      rs       <- st.tcs.get
    } yield {
      assert(rs.oiWfs.observe.path.connected)
      assert(rs.oiWfs.observe.label.connected)
      assert(rs.oiWfs.observe.output.connected)
      assert(rs.oiWfs.observe.options.connected)
      assert(rs.oiWfs.observe.fileName.connected)
      assert(rs.oiWfs.observe.interval.connected)
      assert(rs.oiWfs.observe.numberOfExposures.connected)
      assertEquals(rs.oiWfs.observe.interval.value.flatMap(_.toDoubleOption),
                   testVal.toSeconds.toDouble.some
      )
      assertEquals(rs.oiWfs.observe.numberOfExposures.value.flatMap(_.toIntOption), -1.some)
    }
  }

  test("Stop OIWFS exposures") {
    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.oiwfsStopObserve
      rs       <- st.tcs.get
    } yield {
      assert(rs.oiWfs.stop.connected)
      assertEquals(rs.oiWfs.stop.value, CadDirective.MARK.some)
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
      true
    )

    for {
      x        <- createController
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
      x        <- createController
      (st, ctr) = x
      _        <- st.mcs.update(_.focus(_.follow).replace(TestChannel.State.of("ON")))
      _        <- st.scs.update(_.focus(_.follow).replace(TestChannel.State.of("ON")))
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
      GuidersQualityValues.GuiderQuality(1000, true),
      GuidersQualityValues.GuiderQuality(500, false),
      GuidersQualityValues.GuiderQuality(1500, true)
    )
    for {
      x        <- createController
      (st, ctr) = x
      _        <- st.p1.update(
                    _.copy(
                      flux = TestChannel.State.of(testGuideQuality.pwfs1.flux),
                      centroid = TestChannel.State.of(testGuideQuality.pwfs1.centroidDetected.fold(1, 0))
                    )
                  )
      _        <- st.p2.update(
                    _.copy(
                      flux = TestChannel.State.of(testGuideQuality.pwfs2.flux),
                      centroid = TestChannel.State.of(testGuideQuality.pwfs2.centroidDetected.fold(1, 0))
                    )
                  )
      _        <- st.oi.update(
                    _.copy(
                      flux = TestChannel.State.of(testGuideQuality.oiwfs.flux),
                      centroid = TestChannel.State.of(testGuideQuality.oiwfs.centroidDetected.fold(1, 0))
                    )
                  )
      g        <- ctr.getGuideQuality
      rp1      <- st.p1.get
      rp2      <- st.p2.get
      roi      <- st.oi.get
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

  test("Automatically set OIWFS QL") {
    val testExpTime = TimeSpan.unsafeFromMicroseconds(12345)
    val guideCfg    = TelescopeGuideConfig(
      mountGuide = MountGuideOption.MountGuideOn,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      m2Guide = M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
      dayTimeMode = Some(false),
      probeGuide = none
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(defaultGuideState))
      _        <- ctr.oiwfsObserve(testExpTime)
      r00      <- st.tcs.get
      _        <- ctr.enableGuide(guideCfg)
      r01      <- st.tcs.get
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(guideWithOiState))
      _        <- ctr.oiwfsStopObserve
      r02      <- st.tcs.get
      _        <- ctr.disableGuide
      r03      <- st.tcs.get
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(defaultGuideState))
      _        <- ctr.enableGuide(guideCfg)
      r10      <- st.tcs.get
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(guideWithOiState))
      _        <- ctr.oiwfsObserve(testExpTime)
      r11      <- st.tcs.get
      _        <- ctr.disableGuide
      r12      <- st.tcs.get
      _        <- st.tcs.update(_.focus(_.guideStatus).replace(defaultGuideState))
      _        <- ctr.oiwfsStopObserve
      r13      <- st.tcs.get
    } yield {
      assertEquals(r00.oiWfs.observe.output.value, "QL".some)
      assertEquals(r00.oiWfs.observe.options.value, "DHS".some)
      assertEquals(r00.oiWfs.closedLoop.zernikes2m2.value, "0".some)
      assertEquals(r01.oiWfs.observe.output.value, "".some)
      assertEquals(r01.oiWfs.observe.options.value, "NONE".some)
      assertEquals(r01.oiWfs.closedLoop.zernikes2m2.value, "1".some)
      assertEquals(r02.oiWfs.observe.output.value, "".some)
      assertEquals(r02.oiWfs.observe.options.value, "NONE".some)
      assertEquals(r02.oiWfs.closedLoop.zernikes2m2.value, "1".some)
      assertEquals(r03.oiWfs.observe.output.value, "".some)
      assertEquals(r03.oiWfs.observe.options.value, "NONE".some)
      assertEquals(r03.oiWfs.closedLoop.zernikes2m2.value, "1".some)
      assertEquals(r10.oiWfs.observe.output.value, "".some)
      assertEquals(r10.oiWfs.observe.options.value, "NONE".some)
      assertEquals(r10.oiWfs.closedLoop.zernikes2m2.value, "1".some)
      assertEquals(r11.oiWfs.observe.output.value, "".some)
      assertEquals(r11.oiWfs.observe.options.value, "NONE".some)
      assertEquals(r11.oiWfs.closedLoop.zernikes2m2.value, "1".some)
      assertEquals(r12.oiWfs.observe.output.value, "QL".some)
      assertEquals(r12.oiWfs.observe.options.value, "DHS".some)
      assertEquals(r12.oiWfs.closedLoop.zernikes2m2.value, "0".some)
      assertEquals(r13.oiWfs.observe.output.value, "QL".some)
      assertEquals(r13.oiWfs.observe.options.value, "DHS".some)
      assertEquals(r13.oiWfs.closedLoop.zernikes2m2.value, "0".some)
    }
  }

  test("Set baffles") {
    for {
      x        <- createController
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

  case class StateRefs[F[_]](
    tcs:  Ref[F, TestTcsEpicsSystem.State],
    p1:   Ref[F, TestWfsEpicsSystem.State],
    p2:   Ref[F, TestWfsEpicsSystem.State],
    oi:   Ref[F, TestWfsEpicsSystem.State],
    mcs:  Ref[F, TestMcsEpicsSystem.State],
    scs:  Ref[F, TestScsEpicsSystem.State],
    crcs: Ref[F, TestCrcsEpicsSystem.State],
    ags:  Ref[F, TestAgsEpicsSystem.State]
  )

  def createController: IO[(StateRefs[IO], TcsBaseControllerEpics[IO])] = for {
    tcs  <- Ref.of[IO, TestTcsEpicsSystem.State](TestTcsEpicsSystem.defaultState)
    p1   <- Ref.of[IO, TestWfsEpicsSystem.State](TestWfsEpicsSystem.defaultState)
    p2   <- Ref.of[IO, TestWfsEpicsSystem.State](TestWfsEpicsSystem.defaultState)
    oi   <- Ref.of[IO, TestWfsEpicsSystem.State](TestWfsEpicsSystem.defaultState)
    mcs  <- Ref.of[IO, TestMcsEpicsSystem.State](TestMcsEpicsSystem.defaultState)
    scs  <- Ref.of[IO, TestScsEpicsSystem.State](TestScsEpicsSystem.defaultState)
    crcs <- Ref.of[IO, TestCrcsEpicsSystem.State](TestCrcsEpicsSystem.defaultState)
    ags  <- Ref.of[IO, TestAgsEpicsSystem.State](TestAgsEpicsSystem.defaultState)
    st   <- Ref.of[IO, TcsBaseControllerEpics.State](TcsBaseControllerEpics.State.default)
  } yield (
    StateRefs(tcs, p1, p2, oi, mcs, scs, crcs, ags),
    new TcsBaseControllerEpics[IO](
      EpicsSystems(
        TestTcsEpicsSystem.build(tcs),
        TestWfsEpicsSystem.build("PWFS1", p1),
        TestWfsEpicsSystem.build("PWFS2", p2),
        TestWfsEpicsSystem.build("OIWFS", oi),
        TestMcsEpicsSystem.build(mcs),
        TestScsEpicsSystem.build(scs),
        TestCrcsEpicsSystem.build(crcs),
        TestAgsEpicsSystem.build(ags)
      ),
      DefaultTimeout,
      st
    )
  )

}
