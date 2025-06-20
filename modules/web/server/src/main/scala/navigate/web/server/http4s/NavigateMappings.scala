// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.effect.Sync
import cats.effect.kernel.Ref
import cats.syntax.all.*
import ch.qos.logback.classic.spi.ILoggingEvent
import edu.gemini.schema.util.SchemaStitcher
import edu.gemini.schema.util.SourceResolver
import fs2.Stream
import fs2.concurrent.Topic
import grackle.Env
import grackle.Query.Binding
import grackle.QueryCompiler.Elab
import grackle.QueryCompiler.SelectElaborator
import grackle.Result
import grackle.Schema
import grackle.TypeRef
import grackle.Value
import grackle.Value.AbsentValue
import grackle.Value.BooleanValue
import grackle.Value.EnumValue
import grackle.Value.ListValue
import grackle.Value.NullValue
import grackle.Value.ObjectValue
import grackle.Value.StringValue
import grackle.circe.CirceMapping
import grackle.syntax.given
import io.circe.syntax.*
import lucuma.core.enums.ComaOption
import lucuma.core.enums.GuideProbe
import lucuma.core.enums.Instrument
import lucuma.core.enums.LightSinkName
import lucuma.core.enums.M1Source
import lucuma.core.enums.MountGuideOption
import lucuma.core.enums.TipTiltSource
import lucuma.core.math.Coordinates
import lucuma.core.math.Offset
import lucuma.core.math.Wavelength
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.Observation
import lucuma.core.model.ProbeGuide
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.Gid
import lucuma.core.util.TimeSpan
import mouse.boolean.given
import navigate.model.AcquisitionAdjustment
import navigate.model.FocalPlaneOffset
import navigate.model.FocalPlaneOffset.DeltaX
import navigate.model.FocalPlaneOffset.DeltaY
import navigate.model.HandsetAdjustment
import navigate.model.NavigateState
import navigate.model.PointingCorrections
import navigate.model.ServerConfiguration
import navigate.model.config.NavigateConfiguration
import navigate.model.enums.AcquisitionAdjustmentCommand
import navigate.model.enums.LightSource
import navigate.model.enums.VirtualTelescope
import navigate.server.NavigateEngine
import navigate.server.tcs.AutoparkAowfs
import navigate.server.tcs.AutoparkGems
import navigate.server.tcs.AutoparkOiwfs
import navigate.server.tcs.AutoparkPwfs1
import navigate.server.tcs.AutoparkPwfs2
import navigate.server.tcs.GuideState
import navigate.server.tcs.GuiderConfig
import navigate.server.tcs.GuidersQualityValues
import navigate.server.tcs.InstrumentSpecifics
import navigate.server.tcs.Origin
import navigate.server.tcs.ResetPointing
import navigate.server.tcs.RotatorTrackConfig
import navigate.server.tcs.RotatorTrackingMode
import navigate.server.tcs.ShortcircuitMountFilter
import navigate.server.tcs.ShortcircuitTargetFilter
import navigate.server.tcs.SlewOptions
import navigate.server.tcs.StopGuide
import navigate.server.tcs.Target
import navigate.server.tcs.TargetOffsets
import navigate.server.tcs.TcsBaseController.SwapConfig
import navigate.server.tcs.TcsBaseController.TcsConfig
import navigate.server.tcs.TelescopeState
import navigate.server.tcs.TrackingConfig
import navigate.server.tcs.ZeroChopThrow
import navigate.server.tcs.ZeroGuideOffset
import navigate.server.tcs.ZeroInstrumentOffset
import navigate.server.tcs.ZeroMountDiffTrack
import navigate.server.tcs.ZeroMountOffset
import navigate.server.tcs.ZeroSourceDiffTrack
import navigate.server.tcs.ZeroSourceOffset
import navigate.web.server.OcsBuildInfo

import java.nio.file.Path as JPath
import scala.reflect.classTag

import encoder.given

class NavigateMappings[F[_]: Sync](
  config:                         NavigateConfiguration,
  val server:                     NavigateEngine[F],
  val logTopic:                   Topic[F, ILoggingEvent],
  val guideStateTopic:            Topic[F, GuideState],
  val guidersQualityTopic:        Topic[F, GuidersQualityValues],
  val telescopeStateTopic:        Topic[F, TelescopeState],
  val acquisitionAdjustmentTopic: Topic[F, AcquisitionAdjustment],
  val targetAdjustmentTopic:      Topic[F, TargetOffsets],
  val originAdjustmentTopic:      Topic[F, FocalPlaneOffset],
  val pointingAdjustmentTopic:    Topic[F, PointingCorrections],
  val logBuffer:                  Ref[F, Seq[ILoggingEvent]]
)(
  override val schema:            Schema
) extends CirceMapping[F] {
  import NavigateMappings._

  def guideState: F[Result[GuideState]] =
    server.getGuideState.attempt.map(_.fold(Result.internalError, Result.success))

  def telescopeState: F[Result[TelescopeState]] =
    server.getTelescopeState.attempt.map(_.fold(Result.internalError, Result.success))

  def navigateState: F[Result[NavigateState]] =
    server.getNavigateState.attempt.map(_.fold(Result.internalError, Result.success))

  def targetAdjustmentOffsets: F[Result[TargetOffsets]]   =
    server.getTargetAdjustments.attempt.map(_.fold(Result.internalError, Result.success))
  def originAdjustmentOffset: F[Result[FocalPlaneOffset]] =
    server.getOriginOffset.attempt.map(_.fold(Result.internalError, Result.success))

  def pointingAdjustmentOffset: F[Result[PointingCorrections]] =
    server.getPointingOffset.attempt.map(_.fold(Result.internalError, Result.success))

  def instrumentPort(env: Env): F[Result[Option[Int]]] =
    env
      .get[Instrument]("instrument")
      .map(ins =>
        server.getInstrumentPort(ins).attempt.map(_.fold(Result.internalError, Result.success))
      )
      .getOrElse(
        Result.failure[Option[Int]]("instrumentPort parameter could not be parsed.").pure[F]
      )

  def serverVersion: F[Result[String]] = Result.success(OcsBuildInfo.version).pure[F]

  def serverConfig: F[Result[ServerConfiguration]] = Result
    .success(
      ServerConfiguration(
        OcsBuildInfo.version,
        config.site,
        config.navigateEngine.odb.toString,
        config.lucumaSSO.ssoUrl.toString
      )
    )
    .pure[F]

  def mountFollow(env: Env): F[Result[OperationOutcome]] =
    env
      .get[Boolean]("enable")
      .map { en =>
        server
          .mcsFollow(en)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("mountFollow parameter could not be parsed.").pure[F]
      )

  def rotatorFollow(env: Env): F[Result[OperationOutcome]] =
    env
      .get[Boolean]("enable")
      .map { en =>
        server
          .rotFollow(en)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure("rotatorFollow parameter could not be parsed.").pure[F]
      )

  def rotatorConfig(env: Env): F[Result[OperationOutcome]] =
    env
      .get[RotatorTrackConfig]("config")
      .map { cfg =>
        server
          .rotTrackingConfig(cfg)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure("rotatorConfig parameter could not be parsed.").pure[F]
      )

  def scsFollow(env: Env): F[Result[OperationOutcome]] =
    env
      .get[Boolean]("enable")
      .map { en =>
        server
          .scsFollow(en)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("scsFollow parameter could not be parsed.").pure[F]
      )

  def instrumentSpecifics(env: Env): F[Result[OperationOutcome]] =
    env
      .get[InstrumentSpecifics]("instrumentSpecificsParams")(using classTag[InstrumentSpecifics])
      .map { isp =>
        server
          .instrumentSpecifics(isp)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result
          .failure[OperationOutcome]("InstrumentSpecifics parameters could not be parsed.")
          .pure[F]
      )

  def slew(env: Env): F[Result[OperationOutcome]] = (for {
    oid <- env.get[Option[Observation.Id]]("obsId")
    so  <- env.get[SlewOptions]("slewOptions")
    tc  <- env.get[TcsConfig]("config")
  } yield server
    .slew(so, tc, oid)
    .attempt
    .map(x =>
      Result.success(
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
      )
    )).getOrElse(
    Result.failure[OperationOutcome](s"Slew parameters $env oid could not be parsed.").pure[F]
  )

  def tcsConfig(env: Env): F[Result[OperationOutcome]] =
    env
      .get[TcsConfig]("config")(using classTag[TcsConfig])
      .map { tc =>
        server
          .tcsConfig(tc)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result
          .failure[OperationOutcome]("tcsConfig parameters could not be parsed.")
          .pure[F]
      )

  def swapTarget(env: Env): F[Result[OperationOutcome]] =
    env
      .get[SwapConfig]("swapConfig")(using classTag[SwapConfig])
      .map { t =>
        server
          .swapTarget(t)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result
          .failure[OperationOutcome]("swapTarget parameters could not be parsed.")
          .pure[F]
      )

  def restoreTarget(env: Env): F[Result[OperationOutcome]] =
    env
      .get[TcsConfig]("config")(using classTag[TcsConfig])
      .map { tc =>
        server
          .restoreTarget(tc)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result
          .failure[OperationOutcome]("restoreTarget parameters could not be parsed.")
          .pure[F]
      )

  def oiwfsTarget(env: Env): F[Result[OperationOutcome]] =
    env
      .get[Target]("target")(using classTag[Target])
      .map { oi =>
        server
          .oiwfsTarget(oi)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("oiwfsTarget parameters could not be parsed.").pure[F]
      )

  def oiwfsProbeTracking(env: Env): F[Result[OperationOutcome]] =
    env
      .get[TrackingConfig]("config")(using classTag[TrackingConfig])
      .map { tc =>
        server
          .oiwfsProbeTracking(tc)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result
          .failure[OperationOutcome]("oiwfsProbeTracking parameters could not be parsed.")
          .pure[F]
      )

  def oiwfsFollow(env: Env): F[Result[OperationOutcome]] =
    env
      .get[Boolean]("enable")
      .map { en =>
        server
          .oiwfsFollow(en)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("oiwfsFollow parameter could not be parsed.").pure[F]
      )

  def oiwfsObserve(env: Env): F[Result[OperationOutcome]] =
    env
      .get[TimeSpan]("period")
      .map { p =>
        server
          .oiwfsObserve(p)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("oiwfsObserve parameter could not be parsed.").pure[F]
      )

  def acObserve(env: Env): F[Result[OperationOutcome]] =
    env
      .get[TimeSpan]("period")
      .map { p =>
        server
          .acObserve(p)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("acObserve parameter could not be parsed.").pure[F]
      )

  def guideEnable(env: Env): F[Result[OperationOutcome]] =
    env
      .get[TelescopeGuideConfig]("config")
      .map { cfg =>
        server
          .enableGuide(cfg)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("guideEnable parameters could not be parsed.").pure[F]
      )

  def acquisitionAdjustment(env: Env): F[Result[OperationOutcome]] =
    env
      .get[AcquisitionAdjustment]("adjustment")
      .map { adj =>
        // First publish the adjustment. if the action fails other clients will be informed anyway
        acquisitionAdjustmentTopic.publish1(adj) *>
          // Run the adjustment if the user confirms, preserve the upstream error
          (adj.command === AcquisitionAdjustmentCommand.UserConfirms)
            .valueOrPure[F, Result[OperationOutcome]](
              parameterlessCommand(server.acquisitionAdj(adj.offset, adj.iaa, adj.ipa))
            )(Result.success(OperationOutcome.success))
      }
      .getOrElse {
        Result
          .failure[OperationOutcome]("acquisitionAdjustment parameters could not be parsed.")
          .pure[F]
      }
  def wfsSky(env: Env): F[Result[OperationOutcome]]                = (for {
    wfs <- env.get[GuideProbe]("wfs")
    exp <- env.get[TimeSpan]("period")
  } yield server
    .wfsSky(wfs, exp)
    .attempt
    .map(x =>
      Result.success(
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
      )
    ))
    .getOrElse(Result.failure[OperationOutcome]("WFS Sky parameters could not be parsed.").pure[F])

  def lightpathConfig(env: Env): F[Result[OperationOutcome]] = (for {
    from <- env.get[LightSource]("from")
    to   <- env.get[LightSinkName]("to")
  } yield server
    .lightpathConfig(from, to)
    .attempt
    .map(x =>
      Result.success(
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
      )
    )).getOrElse(Result.failure[OperationOutcome]("Slew parameters could not be parsed.").pure[F])

  def adjustTarget(env: Env): F[Result[OperationOutcome]] = (for {
    target    <- env.get[VirtualTelescope]("target")
    offset    <- env.get[HandsetAdjustment]("offset")
    openLoops <- env.get[Boolean]("openLoops")
  } yield server
    .targetAdjust(target, offset, openLoops)
    .attempt
    .map(x =>
      Result.success(
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
      )
    )).getOrElse(
    Result.failure[OperationOutcome]("Target adjustment parameters could not be parsed.").pure[F]
  )

  def adjustOrigin(env: Env): F[Result[OperationOutcome]] = (for {
    offset    <- env.get[HandsetAdjustment]("offset")
    openLoops <- env.get[Boolean]("openLoops")
  } yield server
    .originAdjust(offset, openLoops)
    .attempt
    .map(x =>
      Result.success(
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
      )
    )).getOrElse(
    Result.failure[OperationOutcome]("Origin adjustment parameters could not be parsed.").pure[F]
  )

  def adjustPointing(env: Env): F[Result[OperationOutcome]] = (for {
    offset <- env.get[HandsetAdjustment]("offset")
  } yield server
    .pointingAdjust(offset)
    .attempt
    .map(x =>
      Result.success(
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
      )
    )).getOrElse(
    Result.failure[OperationOutcome]("Pointing adjustment parameters could not be parsed.").pure[F]
  )

  def resetTargetAdjustment(env: Env): F[Result[OperationOutcome]] = (for {
    target    <- env.get[VirtualTelescope]("target")
    openLoops <- env.get[Boolean]("openLoops")
  } yield server
    .targetOffsetClear(target, openLoops)
    .attempt
    .map(x =>
      Result.success(
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
      )
    )).getOrElse(
    Result.failure[OperationOutcome]("Clear target offset parameters could not be parsed.").pure[F]
  )

  def absorbTargetAdjustment(env: Env): F[Result[OperationOutcome]] = (for {
    target <- env.get[VirtualTelescope]("target")
  } yield server
    .targetOffsetAbsorb(target)
    .attempt
    .map(x =>
      Result.success(
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
      )
    )).getOrElse(
    Result.failure[OperationOutcome]("Absorb target offset parameters could not be parsed.").pure[F]
  )

  def resetOriginAdjustment(env: Env): F[Result[OperationOutcome]] = (for {
    openLoops <- env.get[Boolean]("openLoops")
  } yield server
    .originOffsetClear(openLoops)
    .attempt
    .map(x =>
      Result.success(
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
      )
    )).getOrElse(
    Result.failure[OperationOutcome]("Clear origin offset parameters could not be parsed.").pure[F]
  )

  def parameterlessCommand(cmd: F[Unit]): F[Result[OperationOutcome]] =
    cmd.attempt
      .map(x =>
        Result.Success(
          x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
        )
      )

  val QueryType: TypeRef        = schema.ref("Query")
  val MutationType: TypeRef     = schema.ref("Mutation")
  val SubscriptionType: TypeRef = schema.ref("Subscription")

  override val selectElaborator: SelectElaborator = SelectElaborator {
    case (MutationType, "mountFollow", List(Binding("enable", BooleanValue(en))))               =>
      Elab.env("enable" -> en)
    case (MutationType, "rotatorFollow", List(Binding("enable", BooleanValue(en))))             =>
      Elab.env("enable" -> en)
    case (MutationType, "rotatorConfig", List(Binding("config", ObjectValue(fields))))          =>
      for {
        x <- Elab.liftR(
               parseRotatorConfig(fields).toResult("Could not parse rotatorConfig parameters.")
             )
        _ <- Elab.env("config", x)
      } yield ()
    case (MutationType, "scsFollow", List(Binding("enable", BooleanValue(en))))                 =>
      Elab.env("enable" -> en)
    case (MutationType, "tcsConfig", List(Binding("config", ObjectValue(fields))))              =>
      for {
        x <-
          Elab.liftR(parseTcsConfigInput(fields).toResult("Could not parse TCS config parameters."))
        _ <- Elab.env("config", x)
      } yield ()
    case (MutationType,
          "slew",
          List(Binding("slewOptions", ObjectValue(so)),
               Binding("config", ObjectValue(cf)),
               Binding("obsId", AbsentValue | NullValue)
          )
        ) =>
      for {
        x <-
          Elab.liftR(parseSlewOptionsInput(so).toResult("Could not parse Slew options parameters."))
        _ <- Elab.env("slewOptions" -> x)
        y <- Elab.liftR(parseTcsConfigInput(cf).toResult("Could not parse TCS config parameters."))
        _ <- Elab.env("config" -> y)
        _ <- Elab.env("obsId" -> none[Observation.Id])
      } yield ()
    case (MutationType,
          "slew",
          List(Binding("slewOptions", ObjectValue(so)),
               Binding("config", ObjectValue(cf)),
               Binding("obsId", StringValue(oi))
          )
        ) =>
      for {
        _ <- Elab.env("obsId" -> parseObservationIdInput(oi))
        x <-
          Elab.liftR(parseSlewOptionsInput(so).toResult("Could not parse Slew options parameters."))
        _ <- Elab.env("slewOptions" -> x)
        y <- Elab.liftR(parseTcsConfigInput(cf).toResult("Could not parse TCS config parameters."))
        _ <- Elab.env("config" -> y)
      } yield ()
    case (MutationType, "swapTarget", List(Binding("swapConfig", ObjectValue(fields))))         =>
      for {
        x <-
          Elab.liftR(
            parseSwapConfigInput(fields).toResult("Could not parse swap target parameters.")
          )
        _ <- Elab.env("swapConfig", x)
      } yield ()
    case (MutationType, "restoreTarget", List(Binding("config", ObjectValue(fields))))          =>
      for {
        x <-
          Elab.liftR(
            parseTcsConfigInput(fields).toResult("Could not parse restore target parameters.")
          )
        _ <- Elab.env("config", x)
      } yield ()
    case (MutationType,
          "instrumentSpecifics",
          List(Binding("instrumentSpecificsParams", ObjectValue(fields)))
        ) =>
      for {
        x <- Elab.liftR(
               parseInstrumentSpecificsInput(fields).toResult(
                 "Could not parse instrumentSpecifics parameters."
               )
             )
        _ <- Elab.env("instrumentSpecificsParams" -> x)
      } yield ()
    case (MutationType, "oiwfsTarget", List(Binding("target", ObjectValue(fields))))            =>
      for {
        x <-
          Elab.liftR(parseTargetInput(fields).toResult("Could not parse oiwfsTarget parameters."))
        _ <- Elab.env("target" -> x)
      } yield ()
    case (MutationType, "oiwfsProbeTracking", List(Binding("config", ObjectValue(fields))))     =>
      for {
        x <- Elab.liftR(
               parseTrackingInput(fields).toResult("Could not parse oiwfsProbeTracking parameters.")
             )
        _ <- Elab.env("config" -> x)
      } yield ()
    case (MutationType, "oiwfsFollow", List(Binding("enable", BooleanValue(en))))               =>
      Elab.env("enable" -> en)
    case (MutationType, "oiwfsObserve", List(Binding("period", ObjectValue(fields))))           =>
      for {
        x <- Elab.liftR(
               parseTimeSpan(fields).toResult(
                 "Could not parse oiwfsObserve parameters."
               )
             )
        _ <- Elab.env("period" -> x)
      } yield ()
    case (MutationType, "acObserve", List(Binding("period", ObjectValue(fields))))              =>
      for {
        x <- Elab.liftR(
               parseTimeSpan(fields).toResult(
                 "Could not parse acObserve parameters."
               )
             )
        _ <- Elab.env("period" -> x)
      } yield ()
    case (MutationType, "guideEnable", List(Binding("config", ObjectValue(fields))))            =>
      for {
        x <- Elab.liftR(
               parseGuideConfig(fields).toResult(
                 "Could not parse guideEnable parameters."
               )
             )
        _ <- Elab.env("config" -> x)
      } yield ()
    case (MutationType,
          "lightpathConfig",
          List(Binding("from", EnumValue(f)), Binding("to", EnumValue(t)))
        ) =>
      for {
        from <- Elab.liftR(
                  parseEnumerated[LightSource](f).toResult(
                    "Could not parse lightpathConfig parameter \"from\""
                  )
                )
        to   <- Elab.liftR(
                  parseEnumerated[LightSinkName](t).toResult(
                    "Could not parse lightpathConfig parameter \"to\""
                  )
                )
        _    <- Elab.env("from" -> from)
        _    <- Elab.env("to" -> to)
      } yield ()
    case (MutationType, "acquisitionAdjustment", List(Binding("adjustment", ObjectValue(adj)))) =>
      Elab
        .liftR(
          parseAcquisitionAdjustment(adj)
            .toResult("Could not parse adjustment parameter \"adjustment\"")
        )
        .flatMap { x =>
          Elab.env("adjustment" -> x)
        }
    case (MutationType,
          "wfsSky",
          List(Binding("wfs", EnumValue(wfs)), Binding("period", ObjectValue(fields)))
        ) =>
      for {
        w <- Elab.liftR(
               parseEnumerated[GuideProbe](wfs).toResult(
                 "Could not parse wfsSky parameter \"wfs\""
               )
             )
        t <- Elab.liftR(
               parseTimeSpan(fields).toResult(
                 "Could not parse wfsSky parameter \"period\""
               )
             )
        _ <- Elab.env("wfs" -> w)
        _ <- Elab.env("period" -> t)
      } yield ()
    case (MutationType,
          "adjustTarget",
          List(Binding("target", EnumValue(target)),
               Binding("offset", ObjectValue(offset)),
               Binding("openLoops", BooleanValue(openLoops))
          )
        ) =>
      for {
        t <- Elab.liftR(
               parseEnumerated[VirtualTelescope](target).toResult(
                 "Could not parse adjustTarget parameter \"target\""
               )
             )
        o <- Elab.liftR(
               parseHandsetAdjustment(offset).toResult(
                 "Could not parse adjustTarget parameter \"offset\""
               )
             )
        _ <- Elab.env("target", t)
        _ <- Elab.env("offset", o)
        _ <- Elab.env("openLoops", openLoops)
      } yield ()
    case (MutationType,
          "resetTargetAdjustment",
          List(Binding("target", EnumValue(target)), Binding("openLoops", BooleanValue(openLoops)))
        ) =>
      for {
        t <- Elab.liftR(
               parseEnumerated[VirtualTelescope](target).toResult(
                 "Could not parse resetTargetAdjustment parameter \"target\""
               )
             )
        _ <- Elab.env("target", t)
        _ <- Elab.env("openLoops", openLoops)
      } yield ()
    case (MutationType, "absorbTargetAdjustment", List(Binding("target", EnumValue(target))))   =>
      for {
        t <- Elab.liftR(
               parseEnumerated[VirtualTelescope](target).toResult(
                 "Could not parse absorbTargetAdjustment parameter \"target\""
               )
             )
        _ <- Elab.env("target", t)
      } yield ()
    case (MutationType, "adjustPointing", List(Binding("offset", ObjectValue(offset))))         =>
      for {
        o <- Elab.liftR(
               parseHandsetAdjustment(offset).toResult(
                 "Could not parse adjustPointing parameter \"offset\""
               )
             )
        _ <- Elab.env("offset", o)
      } yield ()
    case (MutationType,
          "adjustOrigin",
          List(Binding("offset", ObjectValue(offset)),
               Binding("openLoops", BooleanValue(openLoops))
          )
        ) =>
      for {
        o <- Elab.liftR(
               parseHandsetAdjustment(offset).toResult(
                 "Could not parse adjustOrigin parameter \"offset\""
               )
             )
        _ <- Elab.env("offset", o)
        _ <- Elab.env("openLoops", openLoops)
      } yield ()
    case (MutationType,
          "resetOriginAdjustment",
          List(Binding("openLoops", BooleanValue(openLoops)))
        ) =>
      Elab.env("openLoops", openLoops)
    case (QueryType, "instrumentPort", List(Binding("instrument", EnumValue(ins))))             =>
      Elab
        .liftR(
          parseEnumerated[Instrument](ins)
            .toResult("Could not parse instrumentPort parameter \"instrument\"")
        )
        .flatMap(x => Elab.env("instrument" -> x))
  }

  override val typeMappings: TypeMappings = TypeMappings(
    List(
      ObjectMapping(
        tpe = QueryType,
        fieldMappings = List(
          RootEffect.computeEncodable("guideState")((_, _) => guideState),
          RootEffect.computeEncodable("telescopeState")((_, _) => telescopeState),
          RootEffect.computeEncodable("navigateState")((_, _) => navigateState),
          RootEffect.computeEncodable("instrumentPort")((_, env) => instrumentPort(env)),
          RootEffect.computeEncodable("serverVersion")((_, _) => serverVersion),
          RootEffect.computeEncodable("targetAdjustmentOffsets")((_, _) => targetAdjustmentOffsets),
          RootEffect.computeEncodable("originAdjustmentOffset")((_, _) => originAdjustmentOffset),
          RootEffect.computeEncodable("pointingAdjustmentOffset")((_, _) =>
            pointingAdjustmentOffset
          ),
          RootEffect.computeEncodable("serverConfiguration")((_, _) => serverConfig)
        )
      ),
      ObjectMapping(
        tpe = MutationType,
        fieldMappings = List(
          RootEffect.computeEncodable("mountPark")((_, _) => parameterlessCommand(server.mcsPark)),
          RootEffect.computeEncodable("mountFollow")((_, env) => mountFollow(env)),
          RootEffect.computeEncodable("rotatorPark")((_, _) =>
            parameterlessCommand(server.rotPark)
          ),
          RootEffect.computeEncodable("rotatorFollow")((_, env) => rotatorFollow(env)),
          RootEffect.computeEncodable("rotatorConfig")((_, env) => rotatorConfig(env)),
          RootEffect.computeEncodable("scsFollow")((_, env) => scsFollow(env)),
          RootEffect.computeEncodable("tcsConfig")((_, env) => tcsConfig(env)),
          RootEffect.computeEncodable("slew")((_, env) => slew(env)),
          RootEffect.computeEncodable("swapTarget")((_, env) => swapTarget(env)),
          RootEffect.computeEncodable("restoreTarget")((_, env) => restoreTarget(env)),
          RootEffect.computeEncodable("instrumentSpecifics")((_, env) => instrumentSpecifics(env)),
          RootEffect.computeEncodable("oiwfsTarget")((_, env) => oiwfsTarget(env)),
          RootEffect.computeEncodable("oiwfsProbeTracking")((_, env) => oiwfsProbeTracking(env)),
          RootEffect.computeEncodable("oiwfsPark")((_, _) =>
            parameterlessCommand(server.oiwfsPark)
          ),
          RootEffect.computeEncodable("oiwfsFollow")((_, env) => oiwfsFollow(env)),
          RootEffect.computeEncodable("oiwfsObserve")((_, env) => oiwfsObserve(env)),
          RootEffect.computeEncodable("oiwfsStopObserve")((_, _) =>
            parameterlessCommand(server.oiwfsStopObserve)
          ),
          RootEffect.computeEncodable("acObserve")((_, env) => acObserve(env)),
          RootEffect.computeEncodable("acStopObserve")((_, _) =>
            parameterlessCommand(server.acStopObserve)
          ),
          RootEffect.computeEncodable("guideEnable")((_, env) => guideEnable(env)),
          RootEffect.computeEncodable("guideDisable")((_, _) =>
            parameterlessCommand(server.disableGuide)
          ),
          RootEffect.computeEncodable("m1Park")((_, _) => parameterlessCommand(server.m1Park)),
          RootEffect.computeEncodable("m1Unpark")((_, _) => parameterlessCommand(server.m1Unpark)),
          RootEffect.computeEncodable("m1OpenLoopOff")((_, _) =>
            parameterlessCommand(server.m1OpenLoopOff)
          ),
          RootEffect.computeEncodable("m1OpenLoopOn")((_, _) =>
            parameterlessCommand(server.m1OpenLoopOn)
          ),
          RootEffect.computeEncodable("m1ZeroFigure")((_, _) =>
            parameterlessCommand(server.m1ZeroFigure)
          ),
          RootEffect.computeEncodable("m1LoadAoFigure")((_, _) =>
            parameterlessCommand(server.m1LoadAoFigure)
          ),
          RootEffect.computeEncodable("m1LoadNonAoFigure")((_, _) =>
            parameterlessCommand(server.m1LoadNonAoFigure)
          ),
          RootEffect.computeEncodable("lightpathConfig")((_, env) => lightpathConfig(env)),
          RootEffect.computeEncodable("acquisitionAdjustment") { (_, env) =>
            acquisitionAdjustment(env)
          },
          RootEffect.computeEncodable("wfsSky") { (_, env) =>
            wfsSky(env)
          },
          RootEffect.computeEncodable("adjustTarget") { (_, env) =>
            adjustTarget(env)
          },
          RootEffect.computeEncodable("adjustPointing") { (_, env) =>
            adjustPointing(env)
          },
          RootEffect.computeEncodable("adjustOrigin") { (_, env) =>
            adjustOrigin(env)
          },
          RootEffect.computeEncodable("resetTargetAdjustment")((_, env) =>
            resetTargetAdjustment(env)
          ),
          RootEffect.computeEncodable("absorbTargetAdjustment")((_, env) =>
            absorbTargetAdjustment(env)
          ),
          RootEffect.computeEncodable("resetLocalPointingAdjustment")((_, _) =>
            parameterlessCommand(server.pointingOffsetClearLocal)
          ),
          RootEffect.computeEncodable("resetGuidePointingAdjustment")((_, _) =>
            parameterlessCommand(server.pointingOffsetClearGuide)
          ),
          RootEffect.computeEncodable("absorbGuidePointingAdjustment")((_, _) =>
            parameterlessCommand(server.pointingOffsetAbsorbGuide)
          ),
          RootEffect.computeEncodable("resetOriginAdjustment")((_, env) =>
            resetOriginAdjustment(env)
          ),
          RootEffect.computeEncodable("absorbOriginAdjustment")((_, _) =>
            parameterlessCommand(server.originOffsetAbsorb)
          )
        )
      ),
      ObjectMapping(
        tpe = SubscriptionType,
        List(
          RootStream.computeCursor("logMessage") { (p, env) =>
            (Stream.evalSeq(logBuffer.get) ++
              logTopic.subscribe(1024))
              .map(_.asJson)
              .map(circeCursor(p, env, _))
              .map(Result.success)
          },
          RootStream.computeCursor("guideState") { (p, env) =>
            guideStateTopic
              .subscribe(1024)
              .map(_.asJson)
              .map(circeCursor(p, env, _))
              .map(Result.success)
          },
          RootStream.computeCursor("guidersQualityValues") { (p, env) =>
            guidersQualityTopic
              .subscribe(1024)
              .map(_.asJson)
              .map(circeCursor(p, env, _))
              .map(Result.success)
          },
          RootStream.computeCursor("telescopeState") { (p, env) =>
            telescopeStateTopic
              .subscribe(1024)
              .map(_.asJson)
              .map(circeCursor(p, env, _))
              .map(Result.success)
          },
          RootStream.computeCursor("navigateState") { (p, env) =>
            server.getNavigateStateStream
              .map(_.asJson)
              .map(circeCursor(p, env, _))
              .map(Result.success)
          },
          RootStream.computeCursor("acquisitionAdjustmentState") { (p, env) =>
            acquisitionAdjustmentTopic
              .subscribe(1024)
              .map(_.asJson)
              .map(circeCursor(p, env, _))
              .map(Result.success)
          },
          RootStream.computeCursor("targetAdjustmentOffsets") { (p, env) =>
            targetAdjustmentTopic
              .subscribe(1024)
              .map(_.asJson)
              .map(circeCursor(p, env, _))
              .map(Result.success)
          },
          RootStream.computeCursor("originAdjustmentOffset") { (p, env) =>
            originAdjustmentTopic
              .subscribe(1024)
              .map(_.asJson)
              .map(circeCursor(p, env, _))
              .map(Result.success)
          },
          RootStream.computeCursor("pointingAdjustmentOffset") { (p, env) =>
            pointingAdjustmentTopic
              .subscribe(1024)
              .map(_.asJson)
              .map(circeCursor(p, env, _))
              .map(Result.success)
          }
        )
      )
    )
  )
}

object NavigateMappings extends GrackleParsers {

  def loadSchema[F[_]: Sync]: F[Result[Schema]] = SchemaStitcher
    .apply[F](JPath.of("navigate.graphql"), SourceResolver.fromResource(getClass.getClassLoader))
    .build

  def apply[F[_]: Sync](
    config:                     NavigateConfiguration,
    server:                     NavigateEngine[F],
    logTopic:                   Topic[F, ILoggingEvent],
    guideStateTopic:            Topic[F, GuideState],
    guidersQualityTopic:        Topic[F, GuidersQualityValues],
    telescopeStateTopic:        Topic[F, TelescopeState],
    acquisitionAdjustmentTopic: Topic[F, AcquisitionAdjustment],
    targetAdjustmentTopic:      Topic[F, TargetOffsets],
    originAdjustmentTopic:      Topic[F, FocalPlaneOffset],
    pointingAdjustmentTopic:    Topic[F, PointingCorrections],
    logBuffer:                  Ref[F, Seq[ILoggingEvent]]
  ): F[NavigateMappings[F]] = loadSchema.flatMap {
    case Result.Success(schema)           =>
      new NavigateMappings[F](
        config,
        server,
        logTopic,
        guideStateTopic,
        guidersQualityTopic,
        telescopeStateTopic,
        acquisitionAdjustmentTopic,
        targetAdjustmentTopic,
        originAdjustmentTopic,
        pointingAdjustmentTopic,
        logBuffer
      )(schema)
        .pure[F]
    case Result.Warning(problems, schema) =>
      new NavigateMappings[F](
        config,
        server,
        logTopic,
        guideStateTopic,
        guidersQualityTopic,
        telescopeStateTopic,
        acquisitionAdjustmentTopic,
        targetAdjustmentTopic,
        originAdjustmentTopic,
        pointingAdjustmentTopic,
        logBuffer
      )(schema)
        .pure[F]
    case Result.Failure(problems)         =>
      Sync[F].raiseError[NavigateMappings[F]](
        new Throwable(
          s"Unable to load schema because: ${problems.map(_.message).toList.mkString(",")}"
        )
      )
    case Result.InternalError(error)      =>
      Sync[F].raiseError[NavigateMappings[F]](
        new Throwable(s"Unable to load schema because: ${error.getMessage}")
      )

  }

  def parseObservationIdInput(oi: String): Option[Observation.Id] =
    parseGid[Observation.Id](oi, "Observation Id").toOption

  def parseSlewOptionsInput(l: List[(String, Value)]): Option[SlewOptions] = for {
    zct  <-
      l.collectFirst { case ("zeroChopThrow", BooleanValue(v)) => v }.map(ZeroChopThrow(_))
    zso  <- l.collectFirst { case ("zeroSourceOffset", BooleanValue(v)) => v }
              .map(ZeroSourceOffset(_))
    zsdt <- l.collectFirst { case ("zeroSourceDiffTrack", BooleanValue(v)) => v }
              .map(ZeroSourceDiffTrack(_))
    zmo  <- l.collectFirst { case ("zeroMountOffset", BooleanValue(v)) => v }
              .map(ZeroMountOffset(_))
    zmdt <- l.collectFirst { case ("zeroMountDiffTrack", BooleanValue(v)) => v }
              .map(ZeroMountDiffTrack(_))
    stf  <- l.collectFirst { case ("shortcircuitTargetFilter", BooleanValue(v)) => v }
              .map(ShortcircuitTargetFilter(_))
    smf  <- l.collectFirst { case ("shortcircuitMountFilter", BooleanValue(v)) => v }
              .map(ShortcircuitMountFilter(_))
    rp   <-
      l.collectFirst { case ("resetPointing", BooleanValue(v)) => v }.map(ResetPointing(_))
    sg   <- l.collectFirst { case ("stopGuide", BooleanValue(v)) => v }.map(StopGuide(_))
    zgo  <- l.collectFirst { case ("zeroGuideOffset", BooleanValue(v)) => v }
              .map(ZeroGuideOffset(_))
    zio  <- l.collectFirst { case ("zeroInstrumentOffset", BooleanValue(v)) => v }
              .map(ZeroInstrumentOffset(_))
    ap1  <-
      l.collectFirst { case ("autoparkPwfs1", BooleanValue(v)) => v }.map(AutoparkPwfs1(_))
    ap2  <-
      l.collectFirst { case ("autoparkPwfs2", BooleanValue(v)) => v }.map(AutoparkPwfs2(_))
    ao   <-
      l.collectFirst { case ("autoparkOiwfs", BooleanValue(v)) => v }.map(AutoparkOiwfs(_))
    ag   <- l.collectFirst { case ("autoparkGems", BooleanValue(v)) => v }.map(AutoparkGems(_))
    aa   <-
      l.collectFirst { case ("autoparkAowfs", BooleanValue(v)) => v }.map(AutoparkAowfs(_))
  } yield SlewOptions(zct, zso, zsdt, zmo, zmdt, stf, smf, rp, sg, zgo, zio, ap1, ap2, ao, ag, aa)

  def parseSiderealTarget(
    name:         String,
    centralWavel: Option[Wavelength],
    l:            List[(String, Value)]
  ): Option[Target.SiderealTarget] = for {
    ra    <- l.collectFirst { case ("ra", ObjectValue(v)) => parseRightAscension(v) }.flatten
    dec   <- l.collectFirst { case ("dec", ObjectValue(v)) => parseDeclination(v) }.flatten
    epoch <- l.collectFirst { case ("epoch", StringValue(v)) => parseEpoch(v) }.flatten
  } yield Target.SiderealTarget(
    name,
    centralWavel,
    Coordinates(ra, dec),
    epoch,
    l.collectFirst { case ("properMotion", ObjectValue(v)) => parseProperMotion(v) }.flatten,
    l.collectFirst { case ("radialVelocity", ObjectValue(v)) => parseRadialVelocity(v) }.flatten,
    l.collectFirst { case ("parallax", ObjectValue(v)) => parseParallax(v) }.flatten
  )

  def parseNonSiderealTarget(
    @annotation.unused name: String,
    @annotation.unused w:    Option[Wavelength],
    @annotation.unused l:    List[(String, Value)]
  ): Option[Target.SiderealTarget] = none

  def parseAzElTarget(
    name: String,
    w:    Option[Wavelength],
    l:    List[(String, Value)]
  ): Option[Target.AzElTarget] = for {
    az <- l.collectFirst { case ("azimuth", ObjectValue(v)) => parseAngle(v) }.flatten
    el <- l.collectFirst { case ("elevation", ObjectValue(v)) => parseAngle(v) }.flatten
  } yield Target.AzElTarget(
    name,
    w,
    Target.AzElCoordinates(
      Target.Azimuth(az),
      Target.Elevation(el)
    )
  )

  def parseEphemerisTarget(
    @annotation.unused name: String,
    @annotation.unused w:    Option[Wavelength],
    @annotation.unused l:    List[(String, Value)]
  ): Option[Target.EphemerisTarget] = none

  def parseTargetInput(l: List[(String, Value)]): Option[Target] = for {
    nm <- l.collectFirst { case ("name", StringValue(v)) => v }
    wv <- l.collectFirst { case ("wavelength", ObjectValue(v)) => parseWavelength(v) } match {
            case Some(None) => None
            case None       => Some(None)
            case x          => x
          }
    bt <- l.collectFirst { case ("sidereal", ObjectValue(v)) => v }
            .flatMap[Target](parseSiderealTarget(nm, wv, _))
            .orElse(
              l.collectFirst { case ("nonsidereal", ObjectValue(v)) => v }
                .flatMap(parseNonSiderealTarget(nm, wv, _))
            )
            .orElse(
              l.collectFirst { case ("azel", ObjectValue(v)) => v }
                .flatMap(parseAzElTarget(nm, wv, _))
            )
  } yield bt

  def parseGid[A: Gid](s: String, name: String): Either[String, A] =
    Gid[A].fromString.getOption(s).toRight(s"'$s' is not a valid $name id")

  def parseGuideTargetInput(l: List[(String, Value)]): Option[Target] = for {
    nm <- l.collectFirst { case ("name", StringValue(v)) => v }
    bt <- l.collectFirst { case ("sidereal", ObjectValue(v)) => v }
            .flatMap[Target](parseSiderealTarget(nm, None, _))
            .orElse(
              l.collectFirst { case ("nonsidereal", ObjectValue(v)) => v }
                .flatMap(parseNonSiderealTarget(nm, None, _))
            )
  } yield bt

  def parseTrackingInput(l: List[(String, Value)]): Option[TrackingConfig] = for {
    aa <- l.collectFirst { case ("nodAchopA", BooleanValue(v)) => v }
    ab <- l.collectFirst { case ("nodAchopB", BooleanValue(v)) => v }
    ba <- l.collectFirst { case ("nodBchopA", BooleanValue(v)) => v }
    bb <- l.collectFirst { case ("nodBchopB", BooleanValue(v)) => v }
  } yield TrackingConfig(aa, ab, ba, bb)

  def parseOrigin(l: List[(String, Value)]): Option[Origin] = for {
    x <- l.collectFirst { case ("x", ObjectValue(v)) => parseDistance(v) }.flatten
    y <- l.collectFirst { case ("y", ObjectValue(v)) => parseDistance(v) }.flatten
  } yield Origin(x, y)

  def parseInstrumentSpecificsInput(l: List[(String, Value)]): Option[InstrumentSpecifics] = for {
    iaa       <- l.collectFirst { case ("iaa", ObjectValue(v)) => parseAngle(v) }.flatten
    focOffset <- l.collectFirst { case ("focusOffset", ObjectValue(v)) => parseDistance(v) }.flatten
    agName    <- l.collectFirst { case ("agName", StringValue(v)) => v }
    origin    <- l.collectFirst { case ("origin", ObjectValue(v)) => parseOrigin(v) }.flatten
  } yield InstrumentSpecifics(iaa, focOffset, agName, origin)

  def parseGuiderConfig(l: List[(String, Value)]): Option[GuiderConfig] = for {
    target   <- l.collectFirst { case ("target", ObjectValue(v)) => parseGuideTargetInput(v) }.flatten
    tracking <- l.collectFirst { case ("tracking", ObjectValue(v)) =>
                  parseTrackingInput(v)
                }.flatten
  } yield GuiderConfig(target, tracking)

  def parseRotatorConfig(l: List[(String, Value)]): Option[RotatorTrackConfig] = for {
    ipa  <- l.collectFirst { case ("ipa", ObjectValue(v)) => parseAngle(v) }.flatten
    mode <- l.collectFirst { case ("mode", EnumValue(v)) =>
              parseEnumerated[RotatorTrackingMode](v)
            }.flatten
  } yield RotatorTrackConfig(ipa, mode)

  def parseTcsConfigInput(l: List[(String, Value)]): Option[TcsConfig] = for {
    t   <- l.collectFirst { case ("sourceATarget", ObjectValue(v)) => parseTargetInput(v) }.flatten
    inp <- l.collectFirst { case ("instParams", ObjectValue(v)) =>
             parseInstrumentSpecificsInput(v)
           }.flatten
    oi  <-
      l.collectFirst { case ("oiwfs", ObjectValue(v)) => parseGuiderConfig(v) } match {
        case Some(None) => None
        case None       => Some(None)
        case x          => x
      }
    rc  <- l.collectFirst { case ("rotator", ObjectValue(v)) => parseRotatorConfig(v) }.flatten
    ins <- l.collectFirst { case ("instrument", EnumValue(v)) =>
             parseEnumerated[Instrument](v)
           }.flatten
  } yield TcsConfig(t, inp, oi, rc, ins)

  def parseSwapConfigInput(l: List[(String, Value)]): Option[SwapConfig] = for {
    t   <- l.collectFirst { case ("guideTarget", ObjectValue(v)) => parseTargetInput(v) }.flatten
    inp <- l.collectFirst { case ("acParams", ObjectValue(v)) =>
             parseInstrumentSpecificsInput(v)
           }.flatten
    rc  <- l.collectFirst { case ("rotator", ObjectValue(v)) => parseRotatorConfig(v) }.flatten
  } yield SwapConfig(t, inp, rc)

  def parseProbeGuide(l: List[(String, Value)]): Option[ProbeGuide] = for {
    f <- l.collectFirst { case ("from", EnumValue(v)) => parseEnumerated[GuideProbe](v) }.flatten
    t <- l.collectFirst { case ("to", EnumValue(v)) => parseEnumerated[GuideProbe](v) }.flatten
  } yield ProbeGuide(f, t)

  def parseGuideConfig(l: List[(String, Value)]): Option[TelescopeGuideConfig] = {
    val m2: List[TipTiltSource] = l.collectFirst { case ("m2Inputs", ListValue(v)) =>
      v.collect { case EnumValue(v) => parseEnumerated[TipTiltSource](v) }.flattenOption
    }.orEmpty

    val m1: Option[M1Source] = l.collectFirst { case ("m1Input", EnumValue(v)) =>
      parseEnumerated[M1Source](v)
    }.flatten

    val coma = l.collectFirst { case ("m2Coma", BooleanValue(v)) => v }.exists(identity)

    val dayTimeMode = l.collectFirst { case ("daytimeMode", BooleanValue(v)) => v }.exists(identity)

    val probeGuide =
      l.collectFirst { case ("probeGuide", ObjectValue(v)) => parseProbeGuide(v) }.flatten

    l.collectFirst { case ("mountOffload", BooleanValue(v)) => v }
      .map { mount =>
        TelescopeGuideConfig(
          MountGuideOption(mount),
          m1.map(M1GuideConfig.M1GuideOn(_)).getOrElse(M1GuideConfig.M1GuideOff),
          m2.isEmpty.fold(
            M2GuideConfig.M2GuideOff,
            M2GuideConfig.M2GuideOn(ComaOption(coma && m1.isDefined), m2.toSet)
          ),
          Some(dayTimeMode),
          probeGuide
        )
      }
  }

  def parseOffset(l: List[(String, Value)]): Option[Offset] =
    for {
      p <- l.collectFirst { case ("p", ObjectValue(v)) => parseAngle(v) }.flatten
      q <- l.collectFirst { case ("q", ObjectValue(v)) => parseAngle(v) }.flatten
    } yield Offset(p.p, q.q)

  def parseAcquisitionAdjustment(l: List[(String, Value)]): Option[AcquisitionAdjustment] =
    for {
      o  <-
        l.collectFirst { case ("offset", ObjectValue(v)) => parseOffset(v) }.flatten
      ipa = l.collectFirst { case ("ipa", ObjectValue(v)) => parseAngle(v) }.flatten
      iaa = l.collectFirst { case ("iaa", ObjectValue(v)) => parseAngle(v) }.flatten
      cmd = l.collectFirst { case ("command", Value.EnumValue(v)) =>
              parseEnumerated[AcquisitionAdjustmentCommand](v)
            }.flatten
    } yield cmd.fold(AcquisitionAdjustment(o, ipa, iaa))(AcquisitionAdjustment(o, ipa, iaa, _))

  def parseHandsetAdjustment(l: List[(String, Value)]): Option[HandsetAdjustment] =
    l.find(_._2 != Value.AbsentValue) match {
      case Some(("horizontalAdjustment", ObjectValue(n)))  =>
        for {
          daz <- n.collectFirst { case ("azimuth", ObjectValue(m)) => parseAngle(m) }.flatten
          del <- n.collectFirst { case ("elevation", ObjectValue(m)) => parseAngle(m) }.flatten
        } yield HandsetAdjustment.HorizontalAdjustment(daz, del)
      case Some(("focalPlaneAdjustment", ObjectValue(n)))  =>
        for {
          dx <- n.collectFirst { case ("deltaX", ObjectValue(m)) => parseAngle(m) }.flatten
          dy <- n.collectFirst { case ("deltaY", ObjectValue(m)) => parseAngle(m) }.flatten
        } yield HandsetAdjustment.FocalPlaneAdjustment(FocalPlaneOffset(DeltaX(dx), DeltaY(dy)))
      case Some(("instrumentAdjustment", ObjectValue(n)))  =>
        parseOffset(n).map(HandsetAdjustment.InstrumentAdjustment.apply)
      case Some(("equatorialAdjustement", ObjectValue(n))) =>
        for {
          dra  <- n.collectFirst { case ("deltaRA", ObjectValue(m)) => parseAngle(m) }.flatten
          ddec <- n.collectFirst { case ("deltaDec", ObjectValue(m)) => parseAngle(m) }.flatten
        } yield HandsetAdjustment.EquatorialAdjustment(dra, ddec)
      case _                                               => none
    }

}
