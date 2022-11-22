// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server

import cats.ApplicativeThrow
import cats.effect.{ Async, Concurrent, Ref, Temporal }
import cats.effect.kernel.Sync
import cats.syntax.all._
import engage.model.EngageCommand.{
  CrcsFollow,
  CrcsMove,
  CrcsPark,
  CrcsStop,
  EcsCarouselMode,
  McsFollow,
  McsPark
}
import engage.model.{ EngageCommand, EngageEvent }
import engage.model.EngageEvent.{ CommandFailure, CommandPaused, CommandStart, CommandSuccess }
import engage.model.config.EngageEngineConfiguration
import engage.model.enums.{ DomeMode, ShutterMode }
import engage.stateengine.StateEngine
import fs2.{ Pipe, Stream }
import lucuma.core.enums.Site
import monocle.{ Focus, Lens }
import squants.Angle

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

trait EngageEngine[F[_]] {
  val systems: Systems[F]

  def eventStream: Stream[F, EngageEvent]

  def mcsPark: F[Unit]

  def mcsFollow(enable: Boolean): F[Unit]

  def rotStop(useBrakes:         Boolean): F[Unit]
  def rotPark: F[Unit]
  def rotFollow(enable:          Boolean): F[Unit]
  def rotMove(angle:             Angle): F[Unit]
  def ecsCarouselMode(
    domeMode:                    DomeMode,
    shutterMode:                 ShutterMode,
    slitHeight:                  Double,
    domeEnable:                  Boolean,
    shutterEnable:               Boolean
  ): F[Unit]
  def ecsVentGatesMove(gateEast: Double, westGate: Double): F[Unit]
}

object EngageEngine {

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

  private case class EngageEngineImpl[F[_]: Concurrent](
    site:    Site,
    systems: Systems[F],
    conf:    EngageEngineConfiguration,
    engine:  StateEngine[F, State, EngageEvent]
  ) extends EngageEngine[F] {
    override def eventStream: Stream[F, EngageEvent] =
      engine.process(startState)

    override def mcsPark: F[Unit] =
      command(engine, McsPark, systems.tcsSouth.mcsPark, Focus[State](_.mcsParkInProgress))

    override def mcsFollow(enable: Boolean): F[Unit] =
      command(engine,
              McsFollow(enable),
              systems.tcsSouth.mcsFollow(enable),
              Focus[State](_.mcsFollowInProgress)
      )

    override def rotStop(useBrakes: Boolean): F[Unit] =
      command(engine,
              CrcsStop(useBrakes),
              systems.tcsSouth.rotStop(useBrakes),
              Focus[State](_.rotStopInProgress)
      )

    override def rotPark: F[Unit] =
      command(engine, CrcsPark, systems.tcsSouth.rotPark, Focus[State](_.rotParkInProgress))

    override def rotFollow(enable: Boolean): F[Unit] =
      command(engine,
              CrcsFollow(enable),
              systems.tcsSouth.rotFollow(enable),
              Focus[State](_.rotFollowInProgress)
      )

    override def rotMove(angle: Angle): F[Unit] =
      command(engine,
              CrcsMove(angle),
              systems.tcsSouth.rotMove(angle),
              Focus[State](_.rotMoveInProgress)
      )

    override def ecsCarouselMode(
      domeMode:      DomeMode,
      shutterMode:   ShutterMode,
      slitHeight:    Double,
      domeEnable:    Boolean,
      shutterEnable: Boolean
    ): F[Unit] = command(
      engine,
      EcsCarouselMode(domeMode, shutterMode, slitHeight, domeEnable, shutterEnable),
      systems.tcsSouth.ecsCarouselMode(domeMode,
                                       shutterMode,
                                       slitHeight,
                                       domeEnable,
                                       shutterEnable
      ),
      Focus[State](_.ecsDomeModeInProgress)
    )

    override def ecsVentGatesMove(gateEast: Double, westGate: Double): F[Unit] = ???
  }

  def build[F[_]: Concurrent](
    site:    Site,
    systems: Systems[F],
    conf:    EngageEngineConfiguration
  ): F[EngageEngine[F]] = StateEngine
    .build[F, State, EngageEvent]
    .map(EngageEngineImpl[F](site, systems, conf, _))

  case class State(
    mcsParkInProgress:         Boolean,
    mcsFollowInProgress:       Boolean,
    rotStopInProgress:         Boolean,
    rotParkInProgress:         Boolean,
    rotFollowInProgress:       Boolean,
    rotMoveInProgress:         Boolean,
    ecsDomeModeInProgress:     Boolean,
    ecsVentGateMoveInProgress: Boolean
  ) {
    lazy val tcsActionInProgress: Boolean =
      mcsParkInProgress ||
        mcsFollowInProgress ||
        rotStopInProgress ||
        rotParkInProgress ||
        rotFollowInProgress ||
        rotMoveInProgress ||
        ecsDomeModeInProgress ||
        ecsVentGateMoveInProgress
  }

  val startState: State = State(
    mcsParkInProgress = false,
    mcsFollowInProgress = false,
    rotStopInProgress = false,
    rotParkInProgress = false,
    rotFollowInProgress = false,
    rotMoveInProgress = false,
    ecsDomeModeInProgress = false,
    ecsVentGateMoveInProgress = false
  )

  private def command[F[_]: ApplicativeThrow](
    engine:  StateEngine[F, State, EngageEvent],
    cmdType: EngageCommand,
    cmd:     F[ApplyCommandResult],
    f:       Lens[State, Boolean]
  ): F[Unit] = engine.offer(
    engine.getState.flatMap { st =>
      if (!st.tcsActionInProgress && !st.mcsParkInProgress) {
        engine
          .modifyState(f.replace(true))
          .as(CommandStart(cmdType)) *>
          engine.lift(cmd.attempt.map {
            case Right(ApplyCommandResult.Paused)    => CommandPaused(cmdType)
            case Right(ApplyCommandResult.Completed) => CommandSuccess(cmdType)
            case Left(e)                             =>
              CommandFailure(cmdType, s"${cmdType.name} command failed with error: ${e.getMessage}")
          }) <*
          engine.modifyState(f.replace(false))
      } else engine.void
    }
  )

}
