// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.enums.Instrument
import lucuma.core.enums.LightSinkName
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import lucuma.core.model.GuideConfig
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.TimeSpan
import navigate.model.enums.CentralBafflePosition
import navigate.model.enums.DeployableBafflePosition
import navigate.model.enums.DomeMode
import navigate.model.enums.LightSource
import navigate.model.enums.ShutterMode
import navigate.server.ApplyCommandResult

trait TcsBaseController[F[_]] {
  import TcsBaseController.*
  def mcsPark: F[ApplyCommandResult]
  def mcsFollow(enable:           Boolean): F[ApplyCommandResult]
  def rotStop(useBrakes:          Boolean): F[ApplyCommandResult]
  def rotPark: F[ApplyCommandResult]
  def rotFollow(enable:           Boolean): F[ApplyCommandResult]
  def rotMove(angle:              Angle): F[ApplyCommandResult]
  def ecsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ): F[ApplyCommandResult]
  def ecsVentGatesMove(gateEast:  Double, westGate:       Double): F[ApplyCommandResult]
  def tcsConfig(config:           TcsConfig): F[ApplyCommandResult]
  def slew(slewOptions:           SlewOptions, tcsConfig: TcsConfig): F[ApplyCommandResult]
  def swapTarget(swapConfig:      SwapConfig): F[ApplyCommandResult]
  def restoreTarget(config:       TcsConfig): F[ApplyCommandResult]
  def instrumentSpecifics(config: InstrumentSpecifics): F[ApplyCommandResult]
  def oiwfsTarget(target:         Target): F[ApplyCommandResult]
  def rotIaa(angle:               Angle): F[ApplyCommandResult]
  def rotTrackingConfig(cfg:      RotatorTrackConfig): F[ApplyCommandResult]
  def oiwfsProbeTracking(config:  TrackingConfig): F[ApplyCommandResult]
  def oiwfsPark: F[ApplyCommandResult]
  def oiwfsFollow(enable:         Boolean): F[ApplyCommandResult]
  def enableGuide(config:         TelescopeGuideConfig): F[ApplyCommandResult]
  def disableGuide: F[ApplyCommandResult]
  def oiwfsObserve(exposureTime:  TimeSpan): F[ApplyCommandResult]
  def oiwfsStopObserve: F[ApplyCommandResult]
  def hrwfsObserve(exposureTime:  TimeSpan): F[ApplyCommandResult]
  def hrwfsStopObserve: F[ApplyCommandResult]
  def baffles(
    central:    CentralBafflePosition,
    deployable: DeployableBafflePosition
  ): F[ApplyCommandResult]
  def scsFollow(enable:           Boolean): F[ApplyCommandResult]
  def lightPath(from:             LightSource, to:        LightSinkName): F[ApplyCommandResult]
  def m1Park: F[ApplyCommandResult]
  def m1Unpark: F[ApplyCommandResult]
  def m1UpdateOn: F[ApplyCommandResult]
  def m1UpdateOff: F[ApplyCommandResult]
  def m1ZeroFigure: F[ApplyCommandResult]
  def m1LoadAoFigure: F[ApplyCommandResult]
  def m1LoadNonAoFigure: F[ApplyCommandResult]
  def acquisitionAdj(offset: Offset, ipa: Option[Angle], iaa: Option[Angle])(
    guide: GuideConfig
  ): F[ApplyCommandResult]

  // Queries
  def getGuideState: F[GuideState]
  def getGuideQuality: F[GuidersQualityValues]
  def getTelescopeState: F[TelescopeState]
  def getInstrumentPorts: F[InstrumentPorts]
}

object TcsBaseController {

  case class TcsConfig(
    sourceATarget:       Target,
    instrumentSpecifics: InstrumentSpecifics,
    oiwfs:               Option[GuiderConfig],
    rotatorTrackConfig:  RotatorTrackConfig,
    instrument:          Instrument
  )

  case class SwapConfig(
    guideTarget:        Target,
    acSpecifics:        InstrumentSpecifics,
    rotatorTrackConfig: RotatorTrackConfig
  ) {
    lazy val toTcsConfig: TcsConfig = TcsConfig(
      guideTarget,
      acSpecifics,
      None,
      rotatorTrackConfig,
      Instrument.AcqCam
    )
  }

  val SystemDefault: String  = "FK5"
  val EquinoxDefault: String = "J2000"
  val FixedSystem: String    = "Fixed"

}
