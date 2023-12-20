// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.effect.Ref
import cats.syntax.all.*
import lucuma.core.math.Angle
import lucuma.core.util.TimeSpan
import navigate.model.enums.DomeMode
import navigate.model.enums.ShutterMode
import navigate.server.ApplyCommandResult

class TcsBaseControllerSim[F[_]: Applicative](guideRef: Ref[F, GuideState]) extends TcsBaseController[F] {
  override def mcsPark: F[ApplyCommandResult] = Applicative[F].pure(ApplyCommandResult.Completed)

  override def mcsFollow(enable: Boolean): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def rotStop(useBrakes: Boolean): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def rotPark: F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def rotFollow(enable: Boolean): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def rotMove(angle: Angle): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def ecsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def ecsVentGatesMove(gateEast: Double, westGate: Double): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def tcsConfig(config: TcsBaseController.TcsConfig): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def slew(
    slewOptions: SlewOptions,
    tcsConfig:   TcsBaseController.TcsConfig
  ): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def instrumentSpecifics(config: InstrumentSpecifics): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def oiwfsTarget(target: Target): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def rotIaa(angle: Angle): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def oiwfsProbeTracking(config: TrackingConfig): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def oiwfsPark: F[ApplyCommandResult] = Applicative[F].pure(ApplyCommandResult.Completed)

  override def oiwfsFollow(enable: Boolean): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def rotTrackingConfig(cfg: RotatorTrackConfig): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def enableGuide(config: TelescopeGuideConfig): F[ApplyCommandResult] = guideRef.set(
    GuideState(
      config.mountGuide,
      config.m1Guide,
      config.m2Guide
    )
  ).as(ApplyCommandResult.Completed)

  override def disableGuide: F[ApplyCommandResult] = guideRef.set(GuideState(false, M1GuideConfig.M1GuideOff, M2GuideConfig.M2GuideOff)).as(ApplyCommandResult.Completed)

  override def oiwfsObserve(exposureTime: TimeSpan, isQL: Boolean): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def oiwfsStopObserve: F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def getGuideState: F[GuideState] = guideRef.get
}
