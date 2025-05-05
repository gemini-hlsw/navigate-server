// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.Applicative
import cats.MonadThrow
import cats.effect.Async
import cats.effect.Ref
import cats.effect.Temporal
import cats.effect.kernel.Sync
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Topic
import io.circe.syntax.*
import lucuma.core.enums.Instrument
import lucuma.core.enums.LightSinkName
import lucuma.core.enums.MountGuideOption
import lucuma.core.enums.Site
import lucuma.core.enums.SlewStage
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import lucuma.core.model.GuideConfig
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.Observation
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.TimeSpan
import monocle.Lens
import monocle.syntax.all.focus
import mouse.all.*
import navigate.model.NavigateCommand
import navigate.model.NavigateCommand.*
import navigate.model.NavigateEvent
import navigate.model.NavigateEvent.CommandFailure
import navigate.model.NavigateEvent.CommandPaused
import navigate.model.NavigateEvent.CommandStart
import navigate.model.NavigateEvent.CommandSuccess
import navigate.model.NavigateState
import navigate.model.config.ControlStrategy
import navigate.model.config.NavigateEngineConfiguration
import navigate.model.enums.DomeMode
import navigate.model.enums.LightSource
import navigate.model.enums.ShutterMode
import navigate.server.tcs.GuideState
import navigate.server.tcs.GuidersQualityValues
import navigate.server.tcs.InstrumentSpecifics
import navigate.server.tcs.RotatorTrackConfig
import navigate.server.tcs.SlewOptions
import navigate.server.tcs.Target
import navigate.server.tcs.TcsBaseController.SwapConfig
import navigate.server.tcs.TcsBaseController.TcsConfig
import navigate.server.tcs.TelescopeState
import navigate.server.tcs.TrackingConfig
import navigate.stateengine.Handler
import navigate.stateengine.StateEngine
import navigate.stateengine.StateEngine.Event
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.dsl.io.*
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import NavigateEvent.NullEvent

trait NavigateEngine[F[_]] {
  val systems: Systems[F]
  def eventStream: Stream[F, NavigateEvent]
  def mcsPark: F[Unit]
  def mcsFollow(enable:                              Boolean): F[Unit]
  def scsFollow(enable:                              Boolean): F[Unit]
  def rotStop(useBrakes:                             Boolean): F[Unit]
  def rotPark: F[Unit]
  def rotFollow(enable:                              Boolean): F[Unit]
  def rotMove(angle:                                 Angle): F[Unit]
  def rotTrackingConfig(cfg:                         RotatorTrackConfig): F[Unit]
  def ecsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ): F[Unit]
  def ecsVentGatesMove(gateEast:                     Double, westGate:       Double): F[Unit]
  def tcsConfig(config:                              TcsConfig): F[Unit]
  def slew(slewOptions:                              SlewOptions, tcsConfig: TcsConfig, oid:     Option[Observation.Id]): F[Unit]
  def instrumentSpecifics(instrumentSpecificsParams: InstrumentSpecifics): F[Unit]
  def oiwfsTarget(target:                            Target): F[Unit]
  def oiwfsProbeTracking(config:                     TrackingConfig): F[Unit]
  def oiwfsPark: F[Unit]
  def oiwfsFollow(enable:                            Boolean): F[Unit]
  def enableGuide(config:                            TelescopeGuideConfig): F[Unit]
  def disableGuide: F[Unit]
  def oiwfsObserve(period:                           TimeSpan): F[Unit]
  def oiwfsStopObserve: F[Unit]
  def acObserve(period:                              TimeSpan): F[Unit]
  def acStopObserve: F[Unit]
  def swapTarget(swapConfig:                         SwapConfig): F[Unit]
  def restoreTarget(config:                          TcsConfig): F[Unit]
  def m1Park: F[Unit]
  def m1Unpark: F[Unit]
  def m1OpenLoopOff: F[Unit]
  def m1OpenLoopOn: F[Unit]
  def m1ZeroFigure: F[Unit]
  def m1LoadAoFigure: F[Unit]
  def m1LoadNonAoFigure: F[Unit]
  def lightpathConfig(from:                          LightSource, to:        LightSinkName): F[Unit]
  def acquisitionAdj(offset:                         Offset, iaa:            Option[Angle], ipa: Option[Angle]): F[Unit]
  def getGuideState: F[GuideState]
  def getGuidersQuality: F[GuidersQualityValues]
  def getTelescopeState: F[TelescopeState]
  def getNavigateState: F[NavigateState]
  def getNavigateStateStream: Stream[F, NavigateState]
  def getInstrumentPort(instrument:                  Instrument): F[Option[Int]]
  def getGuideDemand: F[GuideConfig]
}

object NavigateEngine {

  def failIfNoEmitsWithin[F[_]: Async, A](
    timeout: FiniteDuration,
    msg:     String
  ): Pipe[F, A, A] = in => {
    import scala.concurrent.TimeoutException
    val now = Temporal[F].realTime

    Stream.eval(now.flatMap(Ref[F].of)).flatMap { lastActivityAt =>
      in.evalTap(_ => now.flatMap(lastActivityAt.set))
        .concurrently {
          Stream.repeatEval {
            (now, lastActivityAt.get)
              .mapN(_ - _)
              .flatMap { elapsed =>
                val t = timeout - elapsed

                Sync[F]
                  .raiseError[Unit](new TimeoutException(msg))
                  .whenA(t <= 0.nanos) >> Temporal[F].sleep(t)
              }
          }
        }
    }
  }

  private case class NavigateEngineImpl[F[_]: {Temporal, Logger}](
    site:     Site,
    systems:  Systems[F],
    conf:     NavigateEngineConfiguration,
    engine:   StateEngine[F, State, NavigateEvent],
    stateRef: Ref[F, State],
    topic:    Topic[F, NavigateState]
  ) extends NavigateEngine[F]
      with Http4sClientDsl[F] {

    // We want to support some retries to observe
    private val clientWithRetry = {
      val max             = 4
      var attemptsCounter = 1
      val policy          = RetryPolicy[F] { (attempts: Int) =>
        if (attempts >= max) None
        else {
          attemptsCounter = attemptsCounter + 1
          10.milliseconds.some
        }
      }
      Retry[F](policy)(systems.client)
    }

    private def postTelescopeGuideConfig(gc: GuideConfig): F[Unit] = {
      val postRequest: Request[F] =
        POST(
          gc.asJson,
          conf.observe / "api" / "observe" / "guide"
        )

      // Update guide state in observe if not simulated
      (Logger[F].info(s"Update guide state in observe") *>
        clientWithRetry
          .expect[String](postRequest)
          .void
          .handleErrorWith(r => Logger[F].error(r)("Error posting guide configuration to observe")))
        .whenA(conf.systemControl.observe === ControlStrategy.FullControl)
    }

    private def navigateState(s: State): NavigateState =
      NavigateState(onSwappedTarget = s.onSwappedTarget)

    override def eventStream: Stream[F, NavigateEvent] =
      engine.process(startState).evalMap { case (s, o) =>
        stateRef.set(s) *> topic.publish1(navigateState(s)).as(o)
      }

    override def mcsPark: F[Unit] =
      simpleCommand(engine, McsPark, systems.tcsCommon.mcsPark)

    override def mcsFollow(enable: Boolean): F[Unit] =
      simpleCommand(engine, McsFollow(enable), systems.tcsCommon.mcsFollow(enable))

    override def scsFollow(enable: Boolean): F[Unit] =
      simpleCommand(engine, ScsFollow(enable), systems.tcsCommon.scsFollow(enable))

    override def rotStop(useBrakes: Boolean): F[Unit] =
      simpleCommand(engine, CrcsStop(useBrakes), systems.tcsCommon.rotStop(useBrakes))

    override def rotPark: F[Unit] =
      simpleCommand(engine, CrcsPark, systems.tcsCommon.rotPark)

    override def rotFollow(enable: Boolean): F[Unit] =
      simpleCommand(engine, CrcsFollow(enable), systems.tcsCommon.rotFollow(enable))

    override def rotMove(angle: Angle): F[Unit] =
      simpleCommand(engine, CrcsMove(angle), systems.tcsCommon.rotMove(angle))

    override def ecsCarouselMode(
      domeMode:      DomeMode,
      shutterMode:   ShutterMode,
      slitHeight:    Double,
      domeEnable:    Boolean,
      shutterEnable: Boolean
    ): F[Unit] = simpleCommand(
      engine,
      EcsCarouselMode(domeMode, shutterMode, slitHeight, domeEnable, shutterEnable),
      systems.tcsCommon.ecsCarouselMode(domeMode,
                                        shutterMode,
                                        slitHeight,
                                        domeEnable,
                                        shutterEnable
      )
    )

    // TODO
    override def ecsVentGatesMove(gateEast: Double, westGate: Double): F[Unit] = Applicative[F].unit

    override def tcsConfig(config: TcsConfig): F[Unit] = command(
      engine,
      TcsConfigure,
      transformCommand(
        TcsConfigure,
        Handler.modify[F, State, ApplyCommandResult](_.focus(_.onSwappedTarget).replace(false)) *>
          Handler.fromStream(
            Stream.eval(
              systems.tcsCommon.tcsConfig(config)
            )
          )
      )
    )

    override def slew(
      slewOptions: SlewOptions,
      tcsConfig:   TcsConfig,
      oid:         Option[Observation.Id]
    ): F[Unit] =
      Logger[F].info(s"Starting slew to ${oid}") *>
        command(
          engine,
          Slew,
          transformCommand(
            Slew,
            Handler.modify[F, State, ApplyCommandResult](
              _.focus(_.onSwappedTarget).replace(false)
            ) *>
              Handler.fromStream(
                Stream.eval(
                  systems.tcsCommon
                    .slew(slewOptions, tcsConfig)
                    // if succesful send an event to the odb
                    .flatTap(_ => oid.traverse_(systems.odb.addSlewEvent(_, SlewStage.StartSlew)))
                )
              )
          )
        )

    override def swapTarget(swapConfig: SwapConfig): F[Unit] = command(
      engine,
      SwapTarget,
      transformCommand(
        SwapTarget,
        Handler.modify[F, State, ApplyCommandResult](_.focus(_.onSwappedTarget).replace(true)) *>
          Handler.fromStream(
            Stream.eval(
              systems.tcsCommon.swapTarget(swapConfig)
            )
          )
      )
    )

    override def restoreTarget(config: TcsConfig): F[Unit] = command(
      engine,
      TcsConfigure,
      transformCommand(
        TcsConfigure,
        Handler.modify[F, State, ApplyCommandResult](_.focus(_.onSwappedTarget).replace(false)) *>
          Handler.fromStream(
            Stream.eval(
              systems.tcsCommon.restoreTarget(config)
            )
          )
      )
    )

    override def instrumentSpecifics(instrumentSpecificsParams: InstrumentSpecifics): F[Unit] =
      simpleCommand(
        engine,
        InstSpecifics,
        systems.tcsCommon.instrumentSpecifics(instrumentSpecificsParams)
      )

    override def oiwfsTarget(target: Target): F[Unit] = simpleCommand(
      engine,
      OiwfsTarget,
      systems.tcsCommon.oiwfsTarget(target)
    )

    override def oiwfsProbeTracking(config: TrackingConfig): F[Unit] = simpleCommand(
      engine,
      OiwfsProbeTracking,
      systems.tcsCommon.oiwfsProbeTracking(config)
    )

    override def oiwfsPark: F[Unit] = simpleCommand(
      engine,
      OiwfsPark,
      systems.tcsCommon.oiwfsPark
    )

    override def oiwfsFollow(enable: Boolean): F[Unit] = simpleCommand(
      engine,
      OiwfsFollow(enable),
      systems.tcsCommon.oiwfsFollow(enable)
    )

    override def rotTrackingConfig(cfg: navigate.server.tcs.RotatorTrackConfig): F[Unit] =
      simpleCommand(
        engine,
        RotatorTrackingConfig,
        systems.tcsCommon.rotTrackingConfig(cfg)
      )

    override def enableGuide(config: TelescopeGuideConfig): F[Unit] =
      command(
        engine,
        EnableGuide,
        transformCommand(
          EnableGuide,
          Handler.get[F, State, ApplyCommandResult].flatMap { st =>
            Handler.replace(st.focus(_.guideConfig.tcsGuide).replace(config)) *>
              Handler.fromStream(
                Stream.eval(
                  systems.tcsCommon.enableGuide(config)
                )
              )
          }
        )
      ) *> postTelescopeGuideConfig(GuideConfig(config, None))

    override def disableGuide: F[Unit] = command(
      engine,
      DisableGuide,
      transformCommand(
        DisableGuide,
        Handler.get[F, State, ApplyCommandResult].flatMap { st =>
          Handler.replace[F, State, ApplyCommandResult](
            st.focus(_.guideConfig.tcsGuide)
              .replace(
                TelescopeGuideConfig(MountGuideOption.MountGuideOff,
                                     M1GuideConfig.M1GuideOff,
                                     M2GuideConfig.M2GuideOff,
                                     None,
                                     None
                )
              )
          ) *>
            Handler.fromStream(
              Stream.eval(
                systems.tcsCommon.disableGuide
              )
            )
        }
      )
    ) *> postTelescopeGuideConfig(GuideConfig.defaultGuideConfig)

    override def oiwfsObserve(period: TimeSpan): F[Unit] = command(
      engine,
      OiwfsObserve,
      transformCommand(
        OiwfsObserve,
        Handler.fromStream(
          Stream.eval(
            systems.tcsCommon.oiwfsObserve(period)
          )
        )
      )
    )

    override def oiwfsStopObserve: F[Unit] = simpleCommand(
      engine,
      OiwfsStopObserve,
      systems.tcsCommon.oiwfsStopObserve
    )

    override def acObserve(period: TimeSpan): F[Unit] = command(
      engine,
      AcObserve,
      transformCommand(
        AcObserve,
        Handler.fromStream(
          Stream.eval(
            systems.tcsCommon.hrwfsObserve(period)
          )
        )
      )
    )

    override def acStopObserve: F[Unit] = simpleCommand(
      engine,
      AcStopObserve,
      systems.tcsCommon.hrwfsStopObserve
    )

    override def getGuideState: F[GuideState] = systems.tcsCommon.getGuideState

    override def getGuidersQuality: F[GuidersQualityValues] = systems.tcsCommon.getGuideQuality

    override def getTelescopeState: F[TelescopeState] = systems.tcsCommon.getTelescopeState

    override def getNavigateState: F[NavigateState] =
      stateRef.get.map(s => NavigateState(s.onSwappedTarget))

    override def getNavigateStateStream: Stream[F, NavigateState] =
      topic
        .subscribe(1024)
        .mapAccumulate[Option[NavigateState], Option[NavigateState]](none) { (acc, ss) =>
          (ss.some, if (acc.contains(ss)) none else ss.some)
        }
        .map(_._2)
        .unNone

    override def m1Park: F[Unit] = simpleCommand(
      engine,
      M1Park,
      systems.tcsCommon.m1Park
    )

    override def m1Unpark: F[Unit] = simpleCommand(
      engine,
      M1Unpark,
      systems.tcsCommon.m1Unpark
    )

    override def m1OpenLoopOff: F[Unit] = simpleCommand(
      engine,
      M1OpenLoopOff,
      systems.tcsCommon.m1UpdateOff
    )

    override def m1OpenLoopOn: F[Unit] = simpleCommand(
      engine,
      M1OpenLoopOn,
      systems.tcsCommon.m1UpdateOn
    )

    override def m1ZeroFigure: F[Unit] = simpleCommand(
      engine,
      M1ZeroFigure,
      systems.tcsCommon.m1ZeroFigure
    )

    override def m1LoadAoFigure: F[Unit] = simpleCommand(
      engine,
      M1LoadAoFigure,
      systems.tcsCommon.m1LoadAoFigure
    )

    override def m1LoadNonAoFigure: F[Unit] = simpleCommand(
      engine,
      M1LoadNonAoFigure,
      systems.tcsCommon.m1LoadNonAoFigure
    )

    override def lightpathConfig(from: LightSource, to: LightSinkName): F[Unit] = simpleCommand(
      engine,
      LightPathConfig,
      systems.tcsCommon.lightPath(from, to)
    )

    override def getInstrumentPort(instrument: Instrument): F[Option[Int]] =
      systems.tcsCommon.getInstrumentPorts.map { x =>
        val a = instrument match {
          case Instrument.AcqCam     => 1
          case Instrument.Flamingos2 => x.flamingos2Port
          case Instrument.Ghost      => x.ghostPort
          case Instrument.GmosNorth  => x.gmosPort
          case Instrument.GmosSouth  => x.gmosPort
          case Instrument.Gnirs      => x.gnirsPort
          case Instrument.Gpi        => x.gpiPort
          case Instrument.Gsaoi      => x.gsaoiPort
          case Instrument.Niri       => x.niriPort
          case Instrument.Alopeke    => (site === Site.GN).fold(2, 0)
          case Instrument.Zorro      => (site === Site.GS).fold(2, 0)
          case _                     => 0
        }
        (a =!= 0).option(a)
      }

    override def acquisitionAdj(offset: Offset, iaa: Option[Angle], ipa: Option[Angle]): F[Unit] =
      simpleCommand(
        engine,
        AcquisitionAdjust(offset, ipa, iaa),
        stateRef.get.flatMap(s => systems.tcsCommon.acquisitionAdj(offset, ipa, iaa)(s.guideConfig))
      )

    override def getGuideDemand: F[GuideConfig] = stateRef.get.map(_.guideConfig)
  }

  def build[F[_]: {Temporal, Logger}](
    site:    Site,
    systems: Systems[F],
    conf:    NavigateEngineConfiguration
  ): F[NavigateEngine[F]] = for {
    eng <- StateEngine.build[F, State, NavigateEvent]
    ref <- Ref.of[F, State](startState)
    top <- Topic[F, NavigateState]
  } yield NavigateEngineImpl[F](site, systems, conf, eng, ref, top)

  case class WfsConfigState(
    period:          Option[TimeSpan],
    configuredForQl: Option[Boolean]
  )

  case class State(
    commandInProgress: Option[NavigateCommand],
    guideConfig:       GuideConfig,
    onSwappedTarget:   Boolean
  ) {
    lazy val tcsActionInProgress: Boolean = commandInProgress.isDefined
  }

  val startState: State = State(
    commandInProgress = None,
    guideConfig = GuideConfig.defaultGuideConfig,
    onSwappedTarget = false
  )

  /**
   * This is used for simple commands, just an F that executes the command when evaluated, producing
   * a result. The method takes care of setting/releasing guard flags in the global state and
   * surrounding the evaluation with log messages. An important distinction with the new command
   * method is that here, the command code does not have access to the global state.
   *
   * @param engine
   *   The state machine.
   * @param cmdType:
   *   The command type, used for logs.
   * @param cmd:
   *   The actual command, wrapped in effect F.
   * @param f:
   *   Lens to the command guard flag in the global state.
   * @tparam F:
   *   Type of effect that wraps the command execution.
   * @return
   *   Effect that, when evaluated, will schedule the execution of the command in the state machine.
   */
  private def simpleCommand[F[_]: {MonadThrow, Logger}](
    engine:  StateEngine[F, State, NavigateEvent],
    cmdType: NavigateCommand,
    cmd:     F[ApplyCommandResult]
  ): F[Unit] = engine.offer(
    engine.getState.flatMap { st =>
      if (!st.tcsActionInProgress) {
        engine
          .modifyState(_.focus(_.commandInProgress).replace(cmdType.some))
          .as(CommandStart(cmdType).some) <*
          Handler
            .fromStream[F, State, Event[F, State, NavigateEvent]](
              Stream.eval[F, Event[F, State, NavigateEvent]](
                Logger[F].info(s"Start command ${cmdType.name}") *>
                  cmd.attempt
                    .map(cmdResultToNavigateEvent(cmdType, _))
                    .flatTap(logEvent(_, cmdType))
                    .map(x =>
                      Event(
                        engine
                          .modifyState(_.focus(_.commandInProgress).replace(None))
                          .as(x.some)
                      )
                    )
              )
            )
      } else {
        engine.lift(
          Logger[F]
            .warn(s"Cannot execute command ${cmdType.name} because a TCS command is in progress.")
            .as(NullEvent)
        ) *> engine.void
      }
    }
  )

  /**
   * Similar to simple command, but here the command is a Handler, executed in the state machine.
   * This gives the command access to the global state, and allows it to have several stages.
   * @param engine
   *   The state machine.
   * @param cmdType:
   *   The command type, used for logs.
   * @param cmd:
   *   The actual command, wrapped in a Handle. The command is responsible for generating the Stream
   *   for later scheduling, although there is a helper method for that: transformCommand
   * @param f:
   *   Lens to the command guard flag in the global state.
   * @tparam F:
   *   Type of effect that wraps the command execution.
   * @return
   *   Effect that, when evaluated, will schedule the execution of the command in the state machine.
   */
  private def command[F[_]: {MonadThrow, Logger}](
    engine:  StateEngine[F, State, NavigateEvent],
    cmdType: NavigateCommand,
    cmd:     Handler[F, State, Event[F, State, NavigateEvent], Unit]
  ): F[Unit] = engine.offer(
    engine.getState.flatMap { st =>
      if (!st.tcsActionInProgress) {
        engine
          .modifyState(_.focus(_.commandInProgress).replace(cmdType.some))
          .as(CommandStart(cmdType).some) <* cmd
      } else {
        engine.lift(
          Logger[F]
            .warn(s"Cannot execute command ${cmdType.name} because a TCS command is in progress.")
            .as(NullEvent)
        ) *> engine.void
      }
    }
  )

  private def cmdResultToNavigateEvent(
    cmdType: NavigateCommand,
    result:  Either[Throwable, ApplyCommandResult]
  ): NavigateEvent =
    result match {
      case Right(ApplyCommandResult.Paused)    => CommandPaused(cmdType)
      case Right(ApplyCommandResult.Completed) => CommandSuccess(cmdType)
      case Left(e)                             =>
        CommandFailure(cmdType, s"${cmdType.name} command failed with error: ${e.getMessage}")
    }

  private def transformCommand[F[_]: {MonadThrow, Logger}](
    cmdType: NavigateCommand,
    cmd:     Handler[F, State, ApplyCommandResult, Unit]
  ): Handler[F, State, Event[F, State, NavigateEvent], Unit] =
    Handler(cmd.run.map { ret =>
      Handler.RetVal(
        ret.v,
        ret.s.map { ss =>
          Stream.eval(
            Logger[F].info(s"Start command ${cmdType.name}")
          ) *>
            ss.attempt
              .map(cmdResultToNavigateEvent(cmdType, _))
              .evalTap(logEvent(_, cmdType))
              .map { x =>
                Event(
                  Handler
                    .modify[F, State, Event[F, State, NavigateEvent]](
                      _.focus(_.commandInProgress).replace(None)
                    )
                    .as(x.some)
                )
              }
        }
      )
    })

  private def logEvent[F[_]: Logger](x: NavigateEvent, cmdType: NavigateCommand): F[Unit] =
    val logMessage = s"Command ${cmdType.name} ended with result $x"
    x match
      case _: CommandFailure => Logger[F].error(logMessage)
      case _                 => Logger[F].info(logMessage)

}
