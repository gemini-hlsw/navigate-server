// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model

import cats.kernel.Eq
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import navigate.model.enums.DomeMode
import navigate.model.enums.ShutterMode

sealed trait NavigateCommand extends Product with Serializable

object NavigateCommand {

  case class McsFollow(enable: Boolean)   extends NavigateCommand
  case class ScsFollow(enable: Boolean)   extends NavigateCommand
  case class CrcsFollow(enable: Boolean)  extends NavigateCommand
  case class Pwfs1Follow(enable: Boolean) extends NavigateCommand
  case class Pwfs2Follow(enable: Boolean) extends NavigateCommand
  case class OiwfsFollow(enable: Boolean) extends NavigateCommand
  case class AowfsFollow(enable: Boolean) extends NavigateCommand
  case class Cwfs1Follow(enable: Boolean) extends NavigateCommand
  case class Cwfs2Follow(enable: Boolean) extends NavigateCommand
  case class Cwfs3Follow(enable: Boolean) extends NavigateCommand
  case class Odgw1Follow(enable: Boolean) extends NavigateCommand
  case class Odgw2Follow(enable: Boolean) extends NavigateCommand
  case class Odgw3Follow(enable: Boolean) extends NavigateCommand
  case class Odgw4Follow(enable: Boolean) extends NavigateCommand
  case object McsPark                     extends NavigateCommand
  case object ScsPark                     extends NavigateCommand
  case object CrcsPark                    extends NavigateCommand
  case object Pwfs1Park                   extends NavigateCommand
  case object Pwfs2Park                   extends NavigateCommand
  case object OiwfsPark                   extends NavigateCommand
  case object AowfsPark                   extends NavigateCommand
  case object Cwfs1Park                   extends NavigateCommand
  case object Cwfs2Park                   extends NavigateCommand
  case object Cwfs3Park                   extends NavigateCommand
  case object Odgw1Park                   extends NavigateCommand
  case object Odgw2Park                   extends NavigateCommand
  case object Odgw3Park                   extends NavigateCommand
  case object Odgw4Park                   extends NavigateCommand
  case class CrcsStop(brakes: Boolean)    extends NavigateCommand
  case class CrcsMove(angle: Angle)       extends NavigateCommand
  case class EcsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ) extends NavigateCommand
  case class EcsVentGatesMove(
    gateEast: Double,
    gateWest: Double
  ) extends NavigateCommand
  case object TcsConfigure                extends NavigateCommand
  case object Slew                        extends NavigateCommand
  case object SwapTarget                  extends NavigateCommand
  case object InstSpecifics               extends NavigateCommand
  case object OiwfsTarget                 extends NavigateCommand
  case object OiwfsProbeTracking          extends NavigateCommand
  case object RotatorTrackingConfig       extends NavigateCommand
  case object EnableGuide                 extends NavigateCommand
  case object DisableGuide                extends NavigateCommand
  case object OiwfsObserve                extends NavigateCommand
  case object OiwfsStopObserve            extends NavigateCommand
  case object AcObserve                   extends NavigateCommand
  case object AcStopObserve               extends NavigateCommand
  case object M1Park                      extends NavigateCommand
  case object M1Unpark                    extends NavigateCommand
  case object M1OpenLoopOff               extends NavigateCommand
  case object M1OpenLoopOn                extends NavigateCommand
  case object M1ZeroFigure                extends NavigateCommand
  case object M1LoadAoFigure              extends NavigateCommand
  case object M1LoadNonAoFigure           extends NavigateCommand
  case object LightPathConfig             extends NavigateCommand
  case class AcquisitionAdjust(offset: Offset, ipa: Option[Angle], iaa: Option[Angle])
      extends NavigateCommand

  given Eq[NavigateCommand] = Eq.fromUniversalEquals

  extension (self: NavigateCommand) {
    def name: String = self match {
      case McsFollow(_)               => "Mcs Follow"
      case ScsFollow(_)               => "Scs Follow"
      case CrcsFollow(_)              => "Crcs Follow"
      case Pwfs1Follow(_)             => "Pwfs1 Follow"
      case Pwfs2Follow(_)             => "Pwfs2 Follow"
      case OiwfsFollow(_)             => "Oiwfs Follow"
      case AowfsFollow(_)             => "Aowfs Follow"
      case Cwfs1Follow(_)             => "Cwfs1 Follow"
      case Cwfs2Follow(_)             => "Cwfs2 Follow"
      case Cwfs3Follow(_)             => "Cwfs3 Follow"
      case Odgw1Follow(_)             => "Odgw1 Follow"
      case Odgw2Follow(_)             => "Odgw2 Follow"
      case Odgw3Follow(_)             => "Odgw3 Follow"
      case Odgw4Follow(_)             => "Odgw4 Follow"
      case McsPark                    => "Mcs Park"
      case ScsPark                    => "Scs Park"
      case CrcsPark                   => "Crcs Park"
      case Pwfs1Park                  => "Pwfs1 Park"
      case Pwfs2Park                  => "Pwfs2 Park"
      case OiwfsPark                  => "Oiwfs Park"
      case AowfsPark                  => "Aowfs Park"
      case Cwfs1Park                  => "Cwfs1 Park"
      case Cwfs2Park                  => "Cwfs2 Park"
      case Cwfs3Park                  => "Cwfs3 Park"
      case Odgw1Park                  => "Odgw1 Park"
      case Odgw2Park                  => "Odgw2 Park"
      case Odgw3Park                  => "Odgw3 Park"
      case Odgw4Park                  => "Odgw4 Park"
      case CrcsStop(_)                => "Crcs Stop"
      case CrcsMove(_)                => "Crcs Move"
      case _: EcsCarouselMode         => "Ecs Carousel Mode"
      case _: EcsVentGatesMove        => "Ecs Vent Gates Move"
      case TcsConfigure               => "TCS Configuration"
      case Slew                       => "Slew"
      case SwapTarget                 => "Swap Target"
      case InstSpecifics              => "Instrument Specifics"
      case OiwfsTarget                => "OIWFS"
      case OiwfsProbeTracking         => "OIWFS Probe Tracking"
      case RotatorTrackingConfig      => "CR Tracking Configuration"
      case EnableGuide                => "Guide Enable"
      case DisableGuide               => "Guide Disable"
      case OiwfsObserve               => "Oiwfs Start Exposures"
      case OiwfsStopObserve           => "Oiwfs Stop Exposures"
      case AcObserve                  => "Acquisition Camera Start Exposures"
      case AcStopObserve              => "Acquisition Camera Stop Exposures"
      case M1Park                     => "M1 Park"
      case M1Unpark                   => "M1 Unpark"
      case M1OpenLoopOff              => "M1 Open Loop Off"
      case M1OpenLoopOn               => "M1 Open Loop On"
      case M1ZeroFigure               => "M1 Zero Figure"
      case M1LoadAoFigure             => "M1 Load Ao Figure"
      case M1LoadNonAoFigure          => "M1 Load Non Ao Figure"
      case LightPathConfig            => "Light Path Configuration"
      case AcquisitionAdjust(_, _, _) => "Acquisition Adjustment Offset"
    }
  }

}
