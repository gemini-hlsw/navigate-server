// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.LightSinkName
import lucuma.core.enums.MountGuideOption
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import lucuma.core.model.GuideConfig
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.TimeSpan
import monocle.Focus.focus
import mouse.boolean.*
import navigate.model.enums.CentralBafflePosition
import navigate.model.enums.DeployableBafflePosition
import navigate.model.enums.DomeMode
import navigate.model.enums.LightSource
import navigate.model.enums.ShutterMode
import navigate.server.ApplyCommandResult
import navigate.server.tcs.FollowStatus.*
import navigate.server.tcs.GuidersQualityValues.GuiderQuality
import navigate.server.tcs.ParkStatus.*
import navigate.server.tcs.TcsBaseController.SwapConfig

class TcsBaseControllerSim[F[_]: Sync](
  guideRef:    Ref[F, GuideState],
  telStateRef: Ref[F, TelescopeState]
) extends TcsBaseController[F] {
  override def mcsPark: F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.mount).replace(MechSystemState(Parked, NotFollowing))
    )
    .as(ApplyCommandResult.Completed)

  override def mcsFollow(enable: Boolean): F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.mount).replace(MechSystemState(NotParked, enable.fold(Following, NotFollowing)))
    )
    .as(ApplyCommandResult.Completed)

  override def rotStop(useBrakes: Boolean): F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.crcs.following).replace(NotFollowing)
    )
    .as(ApplyCommandResult.Completed)

  override def rotPark: F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.crcs).replace(MechSystemState(Parked, NotFollowing))
    )
    .as(ApplyCommandResult.Completed)

  override def rotFollow(enable: Boolean): F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.crcs).replace(MechSystemState(NotParked, enable.fold(Following, NotFollowing)))
    )
    .as(ApplyCommandResult.Completed)

  override def rotMove(angle: Angle): F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.crcs.parked).replace(NotParked)
    )
    .as(ApplyCommandResult.Completed)

  override def ecsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def ecsVentGatesMove(gateEast: Double, westGate: Double): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def tcsConfig(config: TcsBaseController.TcsConfig): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def slew(
    slewOptions: SlewOptions,
    tcsConfig:   TcsBaseController.TcsConfig
  ): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def instrumentSpecifics(config: InstrumentSpecifics): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def oiwfsTarget(target: Target): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def rotIaa(angle: Angle): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def oiwfsProbeTracking(config: TrackingConfig): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def oiwfsPark: F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.oiwfs).replace(MechSystemState(Parked, NotFollowing))
    )
    .as(ApplyCommandResult.Completed)

  override def oiwfsFollow(enable: Boolean): F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.oiwfs).replace(MechSystemState(NotParked, enable.fold(Following, NotFollowing)))
    )
    .as(ApplyCommandResult.Completed)

  override def rotTrackingConfig(cfg: RotatorTrackConfig): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def enableGuide(config: TelescopeGuideConfig): F[ApplyCommandResult] = guideRef
    .update(
      _.copy(mountOffload = config.mountGuide, m1Guide = config.m1Guide, m2Guide = config.m2Guide)
    )
    .as(ApplyCommandResult.Completed)

  override def disableGuide: F[ApplyCommandResult] = guideRef
    .update(
      _.copy(mountOffload = MountGuideOption.MountGuideOff,
             m1Guide = M1GuideConfig.M1GuideOff,
             m2Guide = M2GuideConfig.M2GuideOff
      )
    )
    .as(ApplyCommandResult.Completed)

  override def oiwfsObserve(exposureTime: TimeSpan): F[ApplyCommandResult] = guideRef
    .update(_.copy(oiIntegrating = true))
    .as(ApplyCommandResult.Completed)

  override def oiwfsStopObserve: F[ApplyCommandResult] = guideRef
    .update(_.copy(oiIntegrating = false))
    .as(ApplyCommandResult.Completed)

  override def getGuideState: F[GuideState] = guideRef.get

  override def getGuideQuality: F[GuidersQualityValues] =
    for {
      p1Cnts <- Sync[F].delay(1000 + scala.util.Random.between(-100, 100))
      p2Cnts <- Sync[F].delay(1000 + scala.util.Random.between(-100, 100))
      oiCnts <- Sync[F].delay(1000 + scala.util.Random.between(-100, 100))
    } yield GuidersQualityValues(
      pwfs1 = GuiderQuality(p1Cnts, false),
      pwfs2 = GuiderQuality(p2Cnts, false),
      oiwfs = GuiderQuality(oiCnts, false)
    )

  override def baffles(
    central:    CentralBafflePosition,
    deployable: DeployableBafflePosition
  ): F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F]

  override def getTelescopeState: F[TelescopeState] = telStateRef.get

  override def scsFollow(enable: Boolean): F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.scs).replace(MechSystemState(NotParked, enable.fold(Following, NotFollowing)))
    )
    .as(ApplyCommandResult.Completed)

  override def swapTarget(swapConfig: SwapConfig): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def getInstrumentPorts: F[InstrumentPorts] =
    InstrumentPorts(
      flamingos2Port = 1,
      ghostPort = 0,
      gmosPort = 3,
      gnirsPort = 0,
      gpiPort = 0,
      gsaoiPort = 5,
      nifsPort = 0,
      niriPort = 0
    ).pure[F]

  override def lightPath(from: LightSource, to: LightSinkName): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def restoreTarget(config: TcsBaseController.TcsConfig): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def hrwfsObserve(exposureTime: TimeSpan): F[ApplyCommandResult] =
    ApplyCommandResult.Completed.pure[F]

  override def hrwfsStopObserve: F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F]

  override def m1Park: F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F]

  override def m1Unpark: F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F]

  override def m1UpdateOn: F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F]

  override def m1UpdateOff: F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F]

  override def m1ZeroFigure: F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F]

  override def m1LoadAoFigure: F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F]

  override def m1LoadNonAoFigure: F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F]

  override def acquisitionAdj(offset: Offset, ipa: Option[Angle], iaa: Option[Angle])(
    guide: GuideConfig
  ): F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F]
}
