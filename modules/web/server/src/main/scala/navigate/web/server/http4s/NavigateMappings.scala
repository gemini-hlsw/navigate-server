// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
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
import grackle.Path
import grackle.Query
import grackle.Query.Binding
import grackle.QueryCompiler.Elab
import grackle.QueryCompiler.SelectElaborator
import grackle.Result
import grackle.Schema
import grackle.TypeRef
import grackle.Value
import grackle.Value.BooleanValue
import grackle.Value.EnumValue
import grackle.Value.ListValue
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
import lucuma.core.math.Declination
import lucuma.core.math.Epoch
import lucuma.core.math.Offset
import lucuma.core.math.Parallax
import lucuma.core.math.ProperMotion
import lucuma.core.math.RadialVelocity
import lucuma.core.math.RightAscension
import lucuma.core.math.Wavelength
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.ProbeGuide
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.TimeSpan
import mouse.boolean.given
import navigate.model.AcquisitionAdjustment
import navigate.model.Distance
import navigate.model.NavigateState
import navigate.model.enums.AcquisitionAdjustmentCommand
import navigate.model.enums.LightSource
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
  server:                     NavigateEngine[F],
  logTopic:                   Topic[F, ILoggingEvent],
  guideStateTopic:            Topic[F, GuideState],
  guidersQualityTopic:        Topic[F, GuidersQualityValues],
  telescopeStateTopic:        Topic[F, TelescopeState],
  acquisitionAdjustmentTopic: Topic[F, AcquisitionAdjustment],
  logBuffer:                  Ref[F, Seq[ILoggingEvent]]
)(
  override val schema:        Schema
) extends CirceMapping[F] {
  import NavigateMappings._

  def guideState(p: Path, env: Env): F[Result[GuideState]] =
    server.getGuideState.attempt.map(_.fold(Result.internalError, Result.success))

  def telescopeState(p: Path, env: Env): F[Result[TelescopeState]] =
    server.getTelescopeState.attempt.map(_.fold(Result.internalError, Result.success))

  def navigateState(p: Path, env: Env): F[Result[NavigateState]] =
    server.getNavigateState.attempt.map(_.fold(Result.internalError, Result.success))

  def instrumentPort(p: Path, env: Env): F[Result[Option[Int]]] =
    env
      .get[Instrument]("instrument")
      .map(ins =>
        server.getInstrumentPort(ins).attempt.map(_.fold(Result.internalError, Result.success))
      )
      .getOrElse(
        Result.failure[Option[Int]]("instrumentPort parameter could not be parsed.").pure[F]
      )

  def serverVersion: F[Result[String]] = Result.success(OcsBuildInfo.version).pure[F]

  def mountFollow(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def rotatorFollow(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def rotatorConfig(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def scsFollow(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def instrumentSpecifics(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def slew(p: Path, env: Env): F[Result[OperationOutcome]] = (for {
    so <- env.get[SlewOptions]("slewOptions")(using classTag[SlewOptions])
    tc <- env.get[TcsConfig]("config")(using classTag[TcsConfig])
  } yield server
    .slew(so, tc)
    .attempt
    .map(x =>
      Result.success(
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
      )
    )).getOrElse(Result.failure[OperationOutcome]("Slew parameters could not be parsed.").pure[F])

  def tcsConfig(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def swapTarget(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def restoreTarget(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def oiwfsTarget(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def oiwfsProbeTracking(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def oiwfsFollow(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def guideEnable(p: Path, env: Env): F[Result[OperationOutcome]] =
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

  def acquisitionAdjustment(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[AcquisitionAdjustment]("adjustment")
      .map { adj =>
        // First publish the adjustment. if the action fails other clients will be informed anyway
        acquisitionAdjustmentTopic.publish1(adj) *>
          // Run the adjustment if the user confirms, preserve the upstream error
          (adj.command === AcquisitionAdjustmentCommand.UserConfirms)
            .valueOrPure[F, Result[OperationOutcome]](
              simpleCommand(server.acquisitionAdj(adj.offset, adj.iaa, adj.ipa))
            )(Result.success(OperationOutcome.success))
      }
      .getOrElse {
        Result
          .failure[OperationOutcome]("acquisitionAdjustment parameters could not be parsed.")
          .pure[F]
      }

  def lightpathConfig(p: Path, env: Env): F[Result[OperationOutcome]] = (for {
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

  def simpleCommand(cmd: F[Unit]): F[Result[OperationOutcome]] =
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
          List(Binding("slewOptions", ObjectValue(so)), Binding("config", ObjectValue(cf)))
        ) =>
      for {
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
          RootEffect.computeEncodable("guideState")((p, env) => guideState(p, env)),
          RootEffect.computeEncodable("telescopeState")((p, env) => telescopeState(p, env)),
          RootEffect.computeEncodable("navigateState")((p, env) => navigateState(p, env)),
          RootEffect.computeEncodable("instrumentPort")((p, env) => instrumentPort(p, env)),
          RootEffect.computeEncodable("serverVersion")((_, _) => serverVersion)
        )
      ),
      ObjectMapping(
        tpe = MutationType,
        fieldMappings = List(
          RootEffect.computeEncodable("mountPark")((p, env) => simpleCommand(server.mcsPark)),
          RootEffect.computeEncodable("mountFollow")((p, env) => mountFollow(p, env)),
          RootEffect.computeEncodable("rotatorPark")((p, env) => simpleCommand(server.rotPark)),
          RootEffect.computeEncodable("rotatorFollow")((p, env) => rotatorFollow(p, env)),
          RootEffect.computeEncodable("rotatorConfig")((p, env) => rotatorConfig(p, env)),
          RootEffect.computeEncodable("scsFollow")((p, env) => scsFollow(p, env)),
          RootEffect.computeEncodable("tcsConfig")((p, env) => tcsConfig(p, env)),
          RootEffect.computeEncodable("slew")((p, env) => slew(p, env)),
          RootEffect.computeEncodable("swapTarget")((p, env) => swapTarget(p, env)),
          RootEffect.computeEncodable("restoreTarget")((p, env) => restoreTarget(p, env)),
          RootEffect.computeEncodable("instrumentSpecifics")((p, env) =>
            instrumentSpecifics(p, env)
          ),
          RootEffect.computeEncodable("oiwfsTarget")((p, env) => oiwfsTarget(p, env)),
          RootEffect.computeEncodable("oiwfsProbeTracking")((p, env) => oiwfsProbeTracking(p, env)),
          RootEffect.computeEncodable("oiwfsPark")((p, env) => simpleCommand(server.oiwfsPark)),
          RootEffect.computeEncodable("oiwfsFollow")((p, env) => oiwfsFollow(p, env)),
          RootEffect.computeEncodable("oiwfsObserve")((_, env) => oiwfsObserve(env)),
          RootEffect.computeEncodable("oiwfsStopObserve")((_, _) =>
            simpleCommand(server.oiwfsStopObserve)
          ),
          RootEffect.computeEncodable("acObserve")((_, env) => acObserve(env)),
          RootEffect.computeEncodable("acStopObserve")((_, _) =>
            simpleCommand(server.acStopObserve)
          ),
          RootEffect.computeEncodable("guideEnable")((p, env) => guideEnable(p, env)),
          RootEffect.computeEncodable("guideDisable")((p, env) =>
            simpleCommand(server.disableGuide)
          ),
          RootEffect.computeEncodable("m1Park")((p, env) => simpleCommand(server.m1Park)),
          RootEffect.computeEncodable("m1Unpark")((p, env) => simpleCommand(server.m1Unpark)),
          RootEffect.computeEncodable("m1OpenLoopOff")((p, env) =>
            simpleCommand(server.m1OpenLoopOff)
          ),
          RootEffect.computeEncodable("m1OpenLoopOn")((p, env) =>
            simpleCommand(server.m1OpenLoopOn)
          ),
          RootEffect.computeEncodable("m1ZeroFigure")((p, env) =>
            simpleCommand(server.m1ZeroFigure)
          ),
          RootEffect.computeEncodable("m1LoadAoFigure")((p, env) =>
            simpleCommand(server.m1LoadAoFigure)
          ),
          RootEffect.computeEncodable("m1LoadNonAoFigure")((p, env) =>
            simpleCommand(server.m1LoadNonAoFigure)
          ),
          RootEffect.computeEncodable("lightpathConfig")((p, env) => lightpathConfig(p, env)),
          RootEffect.computeEncodable("acquisitionAdjustment") { (p, env) =>
            acquisitionAdjustment(p, env)
          }
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
    server:                     NavigateEngine[F],
    logTopic:                   Topic[F, ILoggingEvent],
    guideStateTopic:            Topic[F, GuideState],
    guidersQualityTopic:        Topic[F, GuidersQualityValues],
    telescopeStateTopic:        Topic[F, TelescopeState],
    acquisitionAdjustmentTopic: Topic[F, AcquisitionAdjustment],
    logBuffer:                  Ref[F, Seq[ILoggingEvent]]
  ): F[NavigateMappings[F]] = loadSchema.flatMap {
    case Result.Success(schema)           =>
      new NavigateMappings[F](server,
                              logTopic,
                              guideStateTopic,
                              guidersQualityTopic,
                              telescopeStateTopic,
                              acquisitionAdjustmentTopic,
                              logBuffer
      )(schema)
        .pure[F]
    case Result.Warning(problems, schema) =>
      new NavigateMappings[F](server,
                              logTopic,
                              guideStateTopic,
                              guidersQualityTopic,
                              telescopeStateTopic,
                              acquisitionAdjustmentTopic,
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

  def parseSlewOptionsInput(l: List[(String, Value)]): Option[SlewOptions] = for {
    zct  <-
      l.collectFirst { case ("zeroChopThrow", BooleanValue(v)) => v }.map(ZeroChopThrow.value(_))
    zso  <- l.collectFirst { case ("zeroSourceOffset", BooleanValue(v)) => v }
              .map(ZeroSourceOffset.value(_))
    zsdt <- l.collectFirst { case ("zeroSourceDiffTrack", BooleanValue(v)) => v }
              .map(ZeroSourceDiffTrack.value(_))
    zmo  <- l.collectFirst { case ("zeroMountOffset", BooleanValue(v)) => v }
              .map(ZeroMountOffset.value(_))
    zmdt <- l.collectFirst { case ("zeroMountDiffTrack", BooleanValue(v)) => v }
              .map(ZeroMountDiffTrack.value(_))
    stf  <- l.collectFirst { case ("shortcircuitTargetFilter", BooleanValue(v)) => v }
              .map(ShortcircuitTargetFilter.value(_))
    smf  <- l.collectFirst { case ("shortcircuitMountFilter", BooleanValue(v)) => v }
              .map(ShortcircuitMountFilter.value(_))
    rp   <-
      l.collectFirst { case ("resetPointing", BooleanValue(v)) => v }.map(ResetPointing.value(_))
    sg   <- l.collectFirst { case ("stopGuide", BooleanValue(v)) => v }.map(StopGuide.value(_))
    zgo  <- l.collectFirst { case ("zeroGuideOffset", BooleanValue(v)) => v }
              .map(ZeroGuideOffset.value(_))
    zio  <- l.collectFirst { case ("zeroInstrumentOffset", BooleanValue(v)) => v }
              .map(ZeroInstrumentOffset.value(_))
    ap1  <-
      l.collectFirst { case ("autoparkPwfs1", BooleanValue(v)) => v }.map(AutoparkPwfs1.value(_))
    ap2  <-
      l.collectFirst { case ("autoparkPwfs2", BooleanValue(v)) => v }.map(AutoparkPwfs2.value(_))
    ao   <-
      l.collectFirst { case ("autoparkOiwfs", BooleanValue(v)) => v }.map(AutoparkOiwfs.value(_))
    ag   <- l.collectFirst { case ("autoparkGems", BooleanValue(v)) => v }.map(AutoparkGems.value(_))
    aa   <-
      l.collectFirst { case ("autoparkAowfs", BooleanValue(v)) => v }.map(AutoparkAowfs.value(_))
  } yield SlewOptions(
    zct,
    zso,
    zsdt,
    zmo,
    zmdt,
    stf,
    smf,
    rp,
    sg,
    zgo,
    zio,
    ap1,
    ap2,
    ao,
    ag,
    aa
  )

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
    name: String,
    w:    Option[Wavelength],
    l:    List[(String, Value)]
  ): Option[Target.SiderealTarget] = none

  def parseEphemerisTarget(
    name: String,
    w:    Option[Wavelength],
    l:    List[(String, Value)]
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
  } yield bt

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
  } yield InstrumentSpecifics(
    iaa,
    focOffset,
    agName,
    origin
  )

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
      o   <-
        l.collectFirst { case ("offset", ObjectValue(v)) => parseOffset(v) }.flatten
      ipa <- l.collectFirst { case ("ipa", ObjectValue(v)) => parseAngle(v) }
      iaa <- l.collectFirst { case ("iaa", ObjectValue(v)) => parseAngle(v) }
      cmd  = l.collectFirst { case ("command", Value.EnumValue(v)) =>
               parseEnumerated[AcquisitionAdjustmentCommand](v)
             }.flatten
    } yield cmd.fold(AcquisitionAdjustment(o, ipa, iaa))(AcquisitionAdjustment(o, ipa, iaa, _))

}
