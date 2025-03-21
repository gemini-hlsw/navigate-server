// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.*
import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all.*
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.LoggerContextVO
import fs2.Stream
import fs2.concurrent.Topic
import io.circe.Decoder
import io.circe.Decoder.Result
import io.circe.DecodingFailure
import io.circe.Json
import lucuma.core.enums.ComaOption
import lucuma.core.enums.Instrument
import lucuma.core.enums.LightSinkName
import lucuma.core.enums.M1Source
import lucuma.core.enums.MountGuideOption
import lucuma.core.enums.TipTiltSource
import lucuma.core.math.Angle
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.Enumerated
import lucuma.core.util.TimeSpan
import lucuma.core.util.Timestamp
import munit.CatsEffectSuite
import navigate.model.NavigateEvent
import navigate.model.NavigateState
import navigate.model.enums.DomeMode
import navigate.model.enums.LightSource
import navigate.model.enums.ShutterMode
import navigate.server.NavigateEngine
import navigate.server.OdbProxy
import navigate.server.Systems
import navigate.server.tcs.FollowStatus
import navigate.server.tcs.FollowStatus.*
import navigate.server.tcs.FollowStatus.Following
import navigate.server.tcs.GuideState
import navigate.server.tcs.GuidersQualityValues
import navigate.server.tcs.InstrumentSpecifics
import navigate.server.tcs.MechSystemState
import navigate.server.tcs.ParkStatus
import navigate.server.tcs.ParkStatus.*
import navigate.server.tcs.ParkStatus.Parked
import navigate.server.tcs.RotatorTrackConfig
import navigate.server.tcs.SlewOptions
import navigate.server.tcs.Target
import navigate.server.tcs.TcsBaseController.SwapConfig
import navigate.server.tcs.TcsBaseController.TcsConfig
import navigate.server.tcs.TcsNorthControllerSim
import navigate.server.tcs.TcsSouthControllerSim
import navigate.server.tcs.TelescopeState
import navigate.server.tcs.TrackingConfig
import navigate.web.server.OcsBuildInfo
import org.http4s.HttpApp
import org.http4s.client.Client
import org.slf4j.Marker
import org.slf4j.event.KeyValuePair

import java.util
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.given

import NavigateMappings.*

class NavigateMappingsSuite extends CatsEffectSuite {
  import NavigateMappingsTest.*
  import NavigateMappingsTest.given

  def extractResult[T: Decoder](j: Json, mutation: String): Option[T] = j.hcursor
    .downField("data")
    .downField(mutation)
    .as[T]
    .toOption

  test("Process mount follow command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun("mutation { mountFollow(enable: true) { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "mountFollow").exists(_ === OperationOutcome.success)
    )

  }

  test("Process mount park command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun("mutation { mountPark { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "mountPark").exists(_ === OperationOutcome.success)
    )

  }

  test("Process SCS follow command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun("mutation { scsFollow(enable: true) { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "scsFollow").exists(_ === OperationOutcome.success)
    )

  }

  test("Process slew command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
                |mutation { slew (
                |  slewOptions: {
                |    zeroChopThrow: true
                |    zeroSourceOffset: true
                |    zeroSourceDiffTrack: true
                |    zeroMountOffset: true
                |    zeroMountDiffTrack: true
                |    shortcircuitTargetFilter: true
                |    shortcircuitMountFilter: true
                |    resetPointing: true
                |    stopGuide: true
                |    zeroGuideOffset: true
                |    zeroInstrumentOffset: true
                |    autoparkPwfs1: true
                |    autoparkPwfs2: true
                |    autoparkOiwfs: true
                |    autoparkGems: true
                |    autoparkAowfs: true
                |  },
                |  config: {
                |    sourceATarget: {
                |      id: "T0001"
                |      name: "Dummy"
                |      sidereal: {
                |        ra: {
                |          hms: "21:15:33"
                |        }
                |        dec: {
                |          dms: "-30:26:38"
                |        }
                |        epoch:"J2000.000"
                |     }
                |      wavelength: {
                |        nanometers: "400"
                |      }
                |    }
                |    instParams: {
                |      iaa: {
                |        degrees: 178.38
                |      }
                |      focusOffset: {
                |         micrometers: 1234
                |      }
                |      agName: "gmos"
                |      origin: {
                |        x: {
                |          micrometers: 3012
                |        }
                |        y: {
                |          micrometers: -1234
                |        }
                |      }
                |    }
                |    oiwfs: {
                |      target: {
                |        name: "OiwfsDummy"
                |        sidereal: {
                |          ra: {
                |            hms: "10:11:12"
                |          }
                |          dec: {
                |            dms: "-30:31:32"
                |          }
                |          epoch:"J2000.000"
                |        }
                |      }
                |      tracking: {
                |        nodAchopA: true
                |        nodAchopB: false
                |        nodBchopA: false
                |        nodBchopB: true
                |      }
                |    }
                |    rotator: {
                |      ipa: {
                |        degrees: 89.76
                |      }
                |      mode: TRACKING
                |    }
                |    instrument: GMOS_NORTH
                |  }
                |) {
                |  result
                |} }
                |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "slew").exists(_ === OperationOutcome.success)
    )
  }

  test("Process TCS configure command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { tcsConfig ( config: {
          |  sourceATarget: {
          |    id: "T0001"
          |    name: "Dummy"
          |    sidereal: {
          |      ra: {
          |        hms: "21:15:33"
          |      }
          |      dec: {
          |        dms: "-30:26:38"
          |      }
          |      epoch:"J2000.000"
          |   }
          |    wavelength: {
          |      nanometers: "400"
          |    }
          |  }
          |  instParams: {
          |    iaa: {
          |      degrees: 178.38
          |    }
          |    focusOffset: {
          |       micrometers: 1234
          |    }
          |    agName: "gmos"
          |    origin: {
          |      x: {
          |        micrometers: 3012
          |      }
          |      y: {
          |        micrometers: -1234
          |      }
          |    }
          |  }
          |  oiwfs: {
          |    target: {
          |      name: "OiwfsDummy"
          |      sidereal: {
          |        ra: {
          |          hms: "10:11:12"
          |        }
          |        dec: {
          |          dms: "-30:31:32"
          |        }
          |        epoch:"J2000.000"
          |      }
          |    }
          |    tracking: {
          |      nodAchopA: true
          |      nodAchopB: false
          |      nodBchopA: false
          |      nodBchopB: true
          |    }
          |  }
          |  rotator: {
          |    ipa: {
          |      microarcseconds: 89.76
          |    }
          |    mode: TRACKING
          |  }
          |  instrument: GMOS_NORTH
          |} ) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "tcsConfig").exists(_ === OperationOutcome.success)
    )
  }

  test("Process swap target command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { swapTarget ( swapConfig: {
          |  guideTarget: {
          |    id: "T0001"
          |    name: "Dummy"
          |    sidereal: {
          |      ra: {
          |        hms: "21:15:33"
          |      }
          |      dec: {
          |        dms: "-30:26:38"
          |      }
          |      epoch:"J2000.000"
          |   }
          |    wavelength: {
          |      nanometers: "400"
          |    }
          |  }
          |  acParams: {
          |    iaa: {
          |      degrees: 178.38
          |    }
          |    focusOffset: {
          |       micrometers: 1234
          |    }
          |    agName: "ac"
          |    origin: {
          |      x: {
          |        micrometers: 3012
          |      }
          |      y: {
          |        micrometers: -1234
          |      }
          |    }
          |  }
          |  rotator: {
          |    ipa: {
          |      microarcseconds: 89.76
          |    }
          |    mode: TRACKING
          |  }
          |} ) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "swapTarget").exists(_ === OperationOutcome.success)
    )
  }

  test("Process restore target  command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { restoreTarget ( config: {
          |  sourceATarget: {
          |    id: "T0001"
          |    name: "Dummy"
          |    sidereal: {
          |      ra: {
          |        hms: "21:15:33"
          |      }
          |      dec: {
          |        dms: "-30:26:38"
          |      }
          |      epoch:"J2000.000"
          |   }
          |    wavelength: {
          |      nanometers: "400"
          |    }
          |  }
          |  instParams: {
          |    iaa: {
          |      degrees: 178.38
          |    }
          |    focusOffset: {
          |       micrometers: 1234
          |    }
          |    agName: "gmos"
          |    origin: {
          |      x: {
          |        micrometers: 3012
          |      }
          |      y: {
          |        micrometers: -1234
          |      }
          |    }
          |  }
          |  oiwfs: {
          |    target: {
          |      name: "OiwfsDummy"
          |      sidereal: {
          |        ra: {
          |          hms: "10:11:12"
          |        }
          |        dec: {
          |          dms: "-30:31:32"
          |        }
          |        epoch:"J2000.000"
          |      }
          |    }
          |    tracking: {
          |      nodAchopA: true
          |      nodAchopB: false
          |      nodBchopA: false
          |      nodBchopB: true
          |    }
          |  }
          |  rotator: {
          |    ipa: {
          |      microarcseconds: 89.76
          |    }
          |    mode: TRACKING
          |  }
          |  instrument: GMOS_NORTH
          |} ) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "restoreTarget").exists(_ === OperationOutcome.success)
    )
  }

  test("Process instrumentSpecifics command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
                |mutation { instrumentSpecifics (instrumentSpecificsParams: {
                |  iaa: {
                |      microarcseconds: 123.432
                |    }
                |    focusOffset: {
                |      millimeters: 54.5432
                |    }
                |    agName: "Test"
                |    origin: {
                |      x: {
                |        millimeters: 12.43
                |      }
                |      y: {
                |        millimeters: 54.54
                |      }
                |    }
                |}) {
                |  result
                |} }
                |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "instrumentSpecifics").exists(
        _ === OperationOutcome.success
      )
    )
  }

  test("Process oiwfsTarget command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
                |mutation { oiwfsTarget (target: {
                |  id: "T0001"
                |  name: "Dummy"
                |  sidereal: {
                |    ra: {
                |      hms: "21:15:33"
                |    }
                |    dec: {
                |      dms: "-30:26:38"
                |    }
                |    epoch:"J2000.000"
                |  }
                |  wavelength: {
                |    nanometers: "400"
                |  }
                |}) {
                |  result
                |} }
                |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "oiwfsTarget").exists(_ === OperationOutcome.success)
    )
  }

  test("Process oiwfsProbeTracking command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { oiwfsProbeTracking (config: {
          |  nodAchopA: true
          |  nodAchopB: false
          |  nodBchopA: false
          |  nodBchopB: true
          |}) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "oiwfsProbeTracking").exists(
        _ === OperationOutcome.success
      )
    )
  }

  test("Process oiwfs follow command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)

      r <- mp.compileAndRun("mutation { oiwfsFollow(enable: true) { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "oiwfsFollow").exists(_ === OperationOutcome.success)
    )
  }

  test("Process oiwfs park command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)

      r <- mp.compileAndRun("mutation { oiwfsPark { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "oiwfsPark").exists(_ === OperationOutcome.success)
    )
  }

  test("Process rotator follow command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun("mutation { rotatorFollow(enable: true) { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "rotatorFollow").exists(_ === OperationOutcome.success)
    )
  }

  test("Process rotator park command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun("mutation { rotatorPark { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "rotatorPark").exists(_ === OperationOutcome.success)
    )
  }

  test("Process rotator tracking configuration command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { rotatorConfig( config: {
          |    ipa: {
          |      microarcseconds: 89.76
          |    }
          |    mode: TRACKING
          |  }
          |) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "rotatorConfig").exists(_ === OperationOutcome.success)
    )
  }

  test("Provide logs subscription") {
    val infoMsg: String    = "info message"
    val warningMsg: String = "warning message"
    val errorMsg: String   = "error message"

    val logEvents = List(
      SimpleLoggingEvent(Timestamp.Min, Level.INFO, "", infoMsg),
      SimpleLoggingEvent(Timestamp.Min, Level.WARN, "", warningMsg),
      SimpleLoggingEvent(Timestamp.Min, Level.ERROR, "", errorMsg)
    )

    def putLogs(topic: Topic[IO, ILoggingEvent]): IO[Unit] =
      logEvents.map(topic.publish1).sequence.void

    for {
      eng  <- buildServer
      log  <- Topic[IO, ILoggingEvent]
      gd   <- Topic[IO, GuideState]
      gq   <- Topic[IO, GuidersQualityValues]
      ts   <- Topic[IO, TelescopeState]
      lb   <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp   <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      logs <- mp.compileAndRunSubscription(
                """
          | subscription {
          |   logMessage {
          |     timestamp
          |     level
          |     thread
          |     message
          |   }
          | }
          |""".stripMargin
              ).map(_.hcursor.downField("data").downField("logMessage").as[SimpleLoggingEvent])
                .take(logEvents.length)
                .compile
                .toList
                .timeout(Duration.fromNanos(10e9))
                .both(putLogs(log).delayBy(Duration.fromNanos(1e9)))
                .map(_._1.collect { case Right(a) => a })
    } yield {
      assert(logs.exists(_.message === infoMsg))
      assert(logs.exists(_.message === warningMsg))
      assert(logs.exists(_.message === errorMsg))
      assertEquals(logEvents, logEvents)
    }
  }

  test("Persist log messages subscription") {
    val debugMsg: String   = "debug message"
    val infoMsg: String    = "info message"
    val warningMsg: String = "warning message"

    val logEvents       = List(
      SimpleLoggingEvent(Timestamp.Min, Level.INFO, "", infoMsg),
      SimpleLoggingEvent(Timestamp.Min, Level.WARN, "", warningMsg)
    )
    val bufferedMessage = SimpleLoggingEvent(Timestamp.Min, Level.DEBUG, "", debugMsg)

    def putLogs(topic: Topic[IO, ILoggingEvent]): IO[Unit] =
      logEvents.map(topic.publish1).sequence.void

    for {
      eng  <- buildServer
      log  <- Topic[IO, ILoggingEvent]
      gd   <- Topic[IO, GuideState]
      gq   <- Topic[IO, GuidersQualityValues]
      ts   <- Topic[IO, TelescopeState]
      lb   <- Ref.of[IO, Seq[ILoggingEvent]](Seq(bufferedMessage))
      mp   <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      logs <- mp.compileAndRunSubscription(
                """
          | subscription {
          |   logMessage {
          |     timestamp
          |     level
          |     thread
          |     message
          |   }
          | }
          |""".stripMargin
              ).map(_.hcursor.downField("data").downField("logMessage").as[SimpleLoggingEvent])
                .take(logEvents.length + 1)
                .compile
                .toList
                .timeout(Duration.fromNanos(10e9))
                .both(putLogs(log).delayBy(Duration.fromNanos(1e9)))
                .map(_._1.collect { case Right(a) => a })
    } yield assertEquals(logs, bufferedMessage +: logEvents)
  }

  test("Provide guide state subscription") {

    val changes: List[GuideState] = List(
      GuideState(
        MountGuideOption.MountGuideOn,
        M1GuideConfig.M1GuideOn(M1Source.OIWFS),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
        false,
        false,
        false,
        false
      ),
      GuideState(MountGuideOption.MountGuideOff,
                 M1GuideConfig.M1GuideOff,
                 M2GuideConfig.M2GuideOff,
                 false,
                 false,
                 false,
                 false
      ),
      GuideState(
        MountGuideOption.MountGuideOff,
        M1GuideConfig.M1GuideOn(M1Source.OIWFS),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
        false,
        false,
        false,
        false
      ),
      GuideState(
        MountGuideOption.MountGuideOff,
        M1GuideConfig.M1GuideOn(M1Source.OIWFS),
        M2GuideConfig.M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
        false,
        false,
        false,
        false
      )
    )

    def putGuideUpdates(topic: Topic[IO, GuideState]): IO[Unit] =
      changes.map(topic.publish1).sequence.void

    val s: IO[List[Result[GuideState]]] = for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      up  <- mp.compileAndRunSubscription(
               """
            | subscription {
            |   guideState {
            |     m2Inputs
            |     m2Coma
            |     m1Input
            |     mountOffload
            |   }
            | }
            |""".stripMargin
             ).map(_.hcursor.downField("data").downField("guideState").as[GuideState])
               .take(changes.length)
               .compile
               .toList
               .timeout(Duration.fromNanos(10e9))
               .both(putGuideUpdates(gd).delayBy(Duration.fromNanos(1e9)))
               .map(_._1)
    } yield up

    s.map { l =>
      val g: List[GuideState] = l.collect { case Right(a) => a }
      assertEquals(g.length, changes.length)
      assertEquals(g, changes)
    }

  }

  test("Query telescope state") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          | query {
          |   telescopeState {
          |     mount {
          |       parked
          |       follow
          |     }
          |     scs {
          |       parked
          |       follow
          |     }
          |     crcs {
          |       parked
          |       follow
          |     }
          |     pwfs1 {
          |       parked
          |       follow
          |     }
          |     pwfs2 {
          |       parked
          |       follow
          |     }
          |     oiwfs {
          |       parked
          |       follow
          |     }
          |   }
          | }
          |""".stripMargin
             )
    } yield assertEquals(r.hcursor.downField("data").downField("telescopeState").as[TelescopeState],
                         TelescopeState.default.asRight[DecodingFailure]
    )
  }

  test("Query Navigate server state") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          | query {
          |   navigateState {
          |     onSwappedTarget
          |   }
          | }
          |""".stripMargin
             )
    } yield assertEquals(r.hcursor.downField("data").downField("navigateState").as[NavigateState],
                         NavigateState.default.asRight[DecodingFailure]
    )
  }

  test("Provide telescope state subscription") {

    val changes: List[TelescopeState] = List(
      TelescopeState(
        MechSystemState(NotParked, Following),
        MechSystemState(NotParked, Following),
        MechSystemState(NotParked, Following),
        MechSystemState(Parked, NotFollowing),
        MechSystemState(Parked, NotFollowing),
        MechSystemState(NotParked, Following)
      ),
      TelescopeState(
        MechSystemState(Parked, NotFollowing),
        MechSystemState(Parked, NotFollowing),
        MechSystemState(Parked, NotFollowing),
        MechSystemState(Parked, NotFollowing),
        MechSystemState(Parked, NotFollowing),
        MechSystemState(Parked, NotFollowing)
      ),
      TelescopeState(
        MechSystemState(NotParked, Following),
        MechSystemState(NotParked, Following),
        MechSystemState(NotParked, Following),
        MechSystemState(Parked, NotFollowing),
        MechSystemState(Parked, NotFollowing),
        MechSystemState(NotParked, Following)
      )
    )

    def putTelescopeUpdates(topic: Topic[IO, TelescopeState]): IO[Unit] =
      changes.map(topic.publish1).sequence.void

    val s: IO[List[Result[TelescopeState]]] = for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      up  <- mp.compileAndRunSubscription(
               """
            | subscription {
            |   telescopeState {
            |     mount {
            |       parked
            |       follow
            |     }
            |     scs {
            |       parked
            |       follow
            |     }
            |     crcs {
            |       parked
            |       follow
            |     }
            |     pwfs1 {
            |       parked
            |       follow
            |     }
            |     pwfs2 {
            |       parked
            |       follow
            |     }
            |     oiwfs {
            |       parked
            |       follow
            |     }
            |   }
            | }
            |""".stripMargin
             ).map(_.hcursor.downField("data").downField("telescopeState").as[TelescopeState])
               .take(changes.length)
               .compile
               .toList
               .timeout(Duration.fromNanos(10e9))
               .both(putTelescopeUpdates(ts).delayBy(Duration.fromNanos(1e9)))
               .map(_._1)
    } yield up

    s.map { l =>
      val g: List[TelescopeState] = l.collect { case Right(a) => a }
      assertEquals(g.length, changes.length)
      assertEquals(g, changes)
    }

  }

  test("Process guide disable command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun("mutation { guideDisable { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "guideDisable").exists(_ === OperationOutcome.success)
    )
  }

  test("Process guide enable command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { guideEnable( config: {
          |    m2Inputs: [ OIWFS ]
          |    m2Coma: true
          |    m1Input: OIWFS
          |    mountOffload: true
          |    daytimeMode: false
          |  }
          |) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "guideEnable").exists(_ === OperationOutcome.success)
    )
  }

  test("Process oiwfs observe command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { oiwfsObserve( period: {
          |    milliseconds: 20
          |  }
          |) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "oiwfsObserve").exists(_ === OperationOutcome.success)
    )
  }

  test("Process oiwfs stop observe command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { oiwfsStopObserve {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "oiwfsStopObserve").exists(_ === OperationOutcome.success)
    )
  }

  test("Process ac observe command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { acObserve( period: {
          |    milliseconds: 20
          |  }
          |) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "acObserve").exists(_ === OperationOutcome.success)
    )
  }

  test("Process ac stop observe command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { acStopObserve {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "acStopObserve").exists(_ === OperationOutcome.success)
    )
  }

  test("Process ac stop observe command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { acStopObserve {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "acStopObserve").exists(_ === OperationOutcome.success)
    )
  }

  def m1Test(name: String, mutation: String) =
    test(s"Process M1 $name command") {
      for {
        eng <- buildServer
        log <- Topic[IO, ILoggingEvent]
        gd  <- Topic[IO, GuideState]
        gq  <- Topic[IO, GuidersQualityValues]
        ts  <- Topic[IO, TelescopeState]
        lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
        mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
        r   <- mp.compileAndRun(
                 s"""
            |mutation { $mutation {
            |  result
            |} }
            |""".stripMargin
               )
      } yield assert(
        extractResult[OperationOutcome](r, mutation).exists(_ === OperationOutcome.success)
      )
    }

  val m1ParkTest: Unit = m1Test("park", "m1Park")

  val m2UnparkTest: Unit = m1Test("unpark", "m1Unpark")

  val m1OpenLoopOffTest: Unit = m1Test("open loop off", "m1OpenLoopOff")

  val m1OpenLoopOnTest: Unit = m1Test("open loop on", "m1OpenLoopOn")

  val m1ZeroFigureTest: Unit = m1Test("zero figure", "m1ZeroFigure")

  val m1LoadAoFigureTest: Unit = m1Test("load AO figure", "m1LoadAoFigure")

  val m1LoanNonAoFigureTest: Unit = m1Test("load non AO figure", "m1LoadNonAoFigure")

  test("Set probeGuide OIWFS to OIWFS") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { guideEnable( config: {
          |    m2Inputs: [ OIWFS ]
          |    m2Coma: true
          |    m1Input: OIWFS
          |    mountOffload: true
          |    daytimeMode: false
          |    probeGuide: {
          |      from: GMOS_OIWFS
          |      to: GMOS_OIWFS
          |    }
          |  }
          |) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "guideEnable").exists(_ === OperationOutcome.success)
    )
  }

  test("Set probeGuide PWFS1 to PWFS2") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      r   <- mp.compileAndRun(
               """
          |mutation { guideEnable( config: {
          |    m2Inputs: [ OIWFS ]
          |    m2Coma: true
          |    m1Input: OIWFS
          |    mountOffload: true
          |    daytimeMode: false
          |    probeGuide: {
          |      from: PWFS_1
          |      to: PWFS_2
          |    }
          |  }
          |) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "guideEnable").exists(_ === OperationOutcome.success)
    )
  }

  test("Configure light path") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      p   <- mp.compileAndRun(
               """
          |mutation { lightpathConfig (
          |  from: SKY,
          |  to: GMOS
          |) {
          |  result
          |} }
          |""".stripMargin
             )
      q   <- mp.compileAndRun(
               """
          |mutation { lightpathConfig (
          |  from: AO,
          |  to: GMOS
          |) {
          |  result
          |} }
          |""".stripMargin
             )
      r   <- mp.compileAndRun(
               """
          |mutation { lightpathConfig (
          |  from: GCAL,
          |  to: GMOS
          |) {
          |  result
          |} }
          |""".stripMargin
             )
      s   <- mp.compileAndRun(
               """
          |mutation { lightpathConfig (
          |  from: SKY,
          |  to: AC
          |) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield {
      assert(
        extractResult[OperationOutcome](p, "lightpathConfig").exists(_ === OperationOutcome.success)
      )
      assert(
        extractResult[OperationOutcome](q, "lightpathConfig").exists(_ === OperationOutcome.success)
      )
      assert(
        extractResult[OperationOutcome](r, "lightpathConfig").exists(_ === OperationOutcome.success)
      )
      assert(
        extractResult[OperationOutcome](s, "lightpathConfig").exists(_ === OperationOutcome.success)
      )
    }
  }

  test("Get instrument port") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      p   <- mp.compileAndRun(
               """
          |query {
          |  instrumentPort( instrument: GMOS_NORTH )
          |}
          |""".stripMargin
             )
      q   <- mp.compileAndRun(
               """
          |query {
          |  instrumentPort( instrument: NICI )
          |}
          |""".stripMargin
             )
    } yield {
      assertEquals(p.hcursor.downField("data").downField("instrumentPort").as[Int].toOption, 5.some)
      assertEquals(q.hcursor.downField("data").downField("instrumentPort").as[Int].toOption, none)
    }
  }

  test("Get server version") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      gd  <- Topic[IO, GuideState]
      gq  <- Topic[IO, GuidersQualityValues]
      ts  <- Topic[IO, TelescopeState]
      lb  <- Ref.empty[IO, Seq[ILoggingEvent]]
      mp  <- NavigateMappings[IO](eng, log, gd, gq, ts, lb)
      p   <- mp.compileAndRun(
               """
          |query {
          |  serverVersion
          |}
          |""".stripMargin
             )
    } yield assertEquals(p.hcursor.downField("data").downField("serverVersion").as[String].toOption,
                         OcsBuildInfo.version.some
    )
  }

}

object NavigateMappingsTest {

  val dummyClient = Client.fromHttpApp(HttpApp.notFound[IO])

  def buildServer: IO[NavigateEngine[IO]] = for {
    r <- Ref.of[IO, GuideState](
           GuideState(MountGuideOption.MountGuideOff,
                      M1GuideConfig.M1GuideOff,
                      M2GuideConfig.M2GuideOff,
                      false,
                      false,
                      false,
                      false
           )
         )
    p <- Ref.of[IO, TelescopeState](TelescopeState.default)
    q <- Ref.of[IO, GuidersQualityValues](
           GuidersQualityValues(
             GuidersQualityValues.GuiderQuality(0, false),
             GuidersQualityValues.GuiderQuality(0, false),
             GuidersQualityValues.GuiderQuality(0, false)
           )
         )
  } yield {
    val tcsSouth = new TcsSouthControllerSim[IO](r, p)
    new NavigateEngine[IO] {

      override val systems: Systems[IO] = Systems(
        OdbProxy.dummy[IO],
        dummyClient,
        tcsSouth,
        tcsSouth,
        new TcsNorthControllerSim[IO](r, p)
      )

      override def eventStream: Stream[IO, NavigateEvent] = Stream.empty

      override def mcsPark: IO[Unit] = IO.unit

      override def mcsFollow(enable: Boolean): IO[Unit] = IO.unit

      override def rotStop(useBrakes: Boolean): IO[Unit] = IO.unit

      override def rotPark: IO[Unit] = IO.unit

      override def rotFollow(enable: Boolean): IO[Unit] = IO.unit

      override def rotMove(angle: Angle): IO[Unit] = IO.unit

      override def ecsCarouselMode(
        domeMode:      DomeMode,
        shutterMode:   ShutterMode,
        slitHeight:    Double,
        domeEnable:    Boolean,
        shutterEnable: Boolean
      ): IO[Unit] = IO.unit

      override def ecsVentGatesMove(gateEast: Double, westGate: Double): IO[Unit] = IO.unit

      override def slew(slewOptions: SlewOptions, config: TcsConfig): IO[Unit] = IO.unit

      override def instrumentSpecifics(instrumentSpecificsParams: InstrumentSpecifics): IO[Unit] =
        IO.unit

      override def oiwfsTarget(target: Target): IO[Unit] = IO.unit

      override def oiwfsProbeTracking(config: TrackingConfig): IO[Unit] = IO.unit

      override def oiwfsPark: IO[Unit] = IO.unit

      override def oiwfsFollow(enable: Boolean): IO[Unit] = IO.unit

      override def rotTrackingConfig(cfg: RotatorTrackConfig): IO[Unit] = IO.unit

      override def enableGuide(config: TelescopeGuideConfig): IO[Unit] = r.update(
        _.copy(
          mountOffload = config.mountGuide,
          m1Guide = config.m1Guide,
          m2Guide = config.m2Guide
        )
      )

      override def disableGuide: IO[Unit] = r.update(
        _.copy(
          mountOffload = MountGuideOption.MountGuideOff,
          m1Guide = M1GuideConfig.M1GuideOff,
          m2Guide = M2GuideConfig.M2GuideOff
        )
      )

      override def tcsConfig(config: TcsConfig): IO[Unit] = IO.unit

      override def oiwfsObserve(period: TimeSpan): IO[Unit] = IO.unit

      override def oiwfsStopObserve: IO[Unit] = IO.unit

      override def acObserve(period: TimeSpan): IO[Unit] = IO.unit

      override def acStopObserve: IO[Unit] = IO.unit

      override def getGuideState: IO[GuideState] = r.get

      override def getGuidersQuality: IO[GuidersQualityValues] = q.get

      override def getTelescopeState: IO[TelescopeState] = p.get

      override def scsFollow(enable: Boolean): IO[Unit] = IO.unit

      override def swapTarget(swapConfig: SwapConfig): IO[Unit] = IO.unit

      override def restoreTarget(config: TcsConfig): IO[Unit] = IO.unit

      override def getNavigateState: IO[NavigateState] = NavigateState.default.pure[IO]

      override def getNavigateStateStream: Stream[IO, NavigateState] =
        Stream.eval(NavigateState.default.pure[IO])

      override def m1Park: IO[Unit] = IO.unit

      override def m1Unpark: IO[Unit] = IO.unit

      override def m1OpenLoopOff: IO[Unit] = IO.unit

      override def m1OpenLoopOn: IO[Unit] = IO.unit

      override def m1ZeroFigure: IO[Unit] = IO.unit

      override def m1LoadAoFigure: IO[Unit] = IO.unit

      override def m1LoadNonAoFigure: IO[Unit] = IO.unit

      override def lightpathConfig(from: LightSource, to: LightSinkName): IO[Unit] = IO.unit

      override def getInstrumentPort(instrument: Instrument): IO[Option[Int]] = (instrument match
        case Instrument.GmosNorth => 5.some
        case _                    => none
      ).pure[IO]
    }
  }

  given Decoder[OperationOutcome] = Decoder.instance(h =>
    h.downField("result")
      .as[OperationResult]
      .map(r => OperationOutcome(r, h.downField("msg").as[String].toOption))
  )

  case class SimpleLoggingEvent(
    timestamp: Timestamp,
    level:     Level,
    thread:    String,
    message:   String
  ) extends ILoggingEvent {

    override def getThreadName: String = thread

    override def getLevel: Level = level

    override def getMessage: String = message

    override def getArgumentArray: Array[AnyRef] = Array.empty

    override def getFormattedMessage: String = message

    override def getLoggerName: String = ""

    override def getLoggerContextVO: LoggerContextVO = new LoggerContextVO("", Map.empty.asJava, 0)

    override def getThrowableProxy: IThrowableProxy = null

    override def getCallerData: Array[StackTraceElement] = Array.empty

    override def hasCallerData: Boolean = false

    override def getMarkerList: util.List[Marker] = List.empty.asJava

    override def getMDCPropertyMap: util.Map[String, String] = Map.empty.asJava

    override def getMdc: util.Map[String, String] = Map.empty.asJava

    override def getTimeStamp: Long = timestamp.toEpochMilli

    override def getNanoseconds: Int = 0

    override def getSequenceNumber: Long = 0

    override def getKeyValuePairs: util.List[KeyValuePair] = List.empty.asJava

    override def prepareForDeferredProcessing(): Unit = ()
  }

  given Decoder[SimpleLoggingEvent] = h =>
    for {
      ts <- h.downField("timestamp").as[Timestamp]
      l  <- h.downField("level").as[String].map(Level.toLevel)
      th <- h.downField("thread").as[String]
      ms <- h.downField("message").as[String]
    } yield SimpleLoggingEvent(ts, l, th, ms)

  given Decoder[GuideState] = h =>
    h.downField("mountOffload").as[Boolean].map { mnt =>
      val m2 = h
        .downField("m2Inputs")
        .as[List[String]]
        .toOption
        .map(_.map(x => Enumerated[TipTiltSource].fromTag(x.toLowerCase.capitalize)).flattenOption)
      val m1 = h
        .downField("m1Input")
        .as[String]
        .toOption
        .flatMap(x => Enumerated[M1Source].fromTag(x.toLowerCase.capitalize))
      val cm = h.downField("m2Coma").as[Boolean].toOption

      GuideState(
        MountGuideOption.fromBoolean(mnt),
        m1.map(M1GuideConfig.M1GuideOn.apply).getOrElse(M1GuideConfig.M1GuideOff),
        m2.map(l =>
          if (l.isEmpty) M2GuideConfig.M2GuideOff
          else M2GuideConfig.M2GuideOn(ComaOption.fromBoolean(cm.exists(identity)), l.toSet)
        ).getOrElse(M2GuideConfig.M2GuideOff),
        false,
        false,
        false,
        false
      )
    }

  given Decoder[MechSystemState] = h =>
    for {
      prk <- h.downField("parked").as[ParkStatus]
      flw <- h.downField("follow").as[FollowStatus]
    } yield MechSystemState(prk, flw)

  given Decoder[TelescopeState] = h =>
    for {
      mnt  <- h.downField("mount").as[MechSystemState]
      scs  <- h.downField("scs").as[MechSystemState]
      crcs <- h.downField("crcs").as[MechSystemState]
      p1   <- h.downField("pwfs1").as[MechSystemState]
      p2   <- h.downField("pwfs2").as[MechSystemState]
      oi   <- h.downField("oiwfs").as[MechSystemState]
    } yield TelescopeState(
      mnt,
      scs,
      crcs,
      p1,
      p2,
      oi
    )

  given Decoder[NavigateState] = h =>
    for {
      swp <- h.downField("onSwappedTarget").as[Boolean]
    } yield NavigateState(swp)

}
