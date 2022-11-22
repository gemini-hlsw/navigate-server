// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model

import cats.kernel.Eq
import engage.model.enums.DomeMode
import engage.model.enums.ShutterMode
import squants.space.Angle

sealed trait EngageCommand extends Product with Serializable

object EngageCommand {

  case class McsFollow(enable: Boolean)   extends EngageCommand
  case class ScsFollow(enable: Boolean)   extends EngageCommand
  case class CrcsFollow(enable: Boolean)  extends EngageCommand
  case class Pwfs1Follow(enable: Boolean) extends EngageCommand
  case class Pwfs2Follow(enable: Boolean) extends EngageCommand
  case class OiwfsFollow(enable: Boolean) extends EngageCommand
  case class AowfsFollow(enable: Boolean) extends EngageCommand
  case class Cwfs1Follow(enable: Boolean) extends EngageCommand
  case class Cwfs2Follow(enable: Boolean) extends EngageCommand
  case class Cwfs3Follow(enable: Boolean) extends EngageCommand
  case class Odgw1Follow(enable: Boolean) extends EngageCommand
  case class Odgw2Follow(enable: Boolean) extends EngageCommand
  case class Odgw3Follow(enable: Boolean) extends EngageCommand
  case class Odgw4Follow(enable: Boolean) extends EngageCommand
  case object McsPark                     extends EngageCommand
  case object ScsPark                     extends EngageCommand
  case object CrcsPark                    extends EngageCommand
  case object Pwfs1Park                   extends EngageCommand
  case object Pwfs2Park                   extends EngageCommand
  case object OiwfsPark                   extends EngageCommand
  case object AowfsPark                   extends EngageCommand
  case object Cwfs1Park                   extends EngageCommand
  case object Cwfs2Park                   extends EngageCommand
  case object Cwfs3Park                   extends EngageCommand
  case object Odgw1Park                   extends EngageCommand
  case object Odgw2Park                   extends EngageCommand
  case object Odgw3Park                   extends EngageCommand
  case object Odgw4Park                   extends EngageCommand
  case class CrcsStop(brakes: Boolean)    extends EngageCommand
  case class CrcsMove(angle: Angle)       extends EngageCommand
  case class EcsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ) extends EngageCommand
  case class EcsVentGatesMove(
    gateEast: Double,
    gateWest: Double
  ) extends EngageCommand

  implicit val engageCommandEq: Eq[EngageCommand] = Eq.fromUniversalEquals

  implicit class EngageCommandOps(self: EngageCommand) {
    def name: String = self match {
      case McsFollow(_)        => "Mcs Follow"
      case ScsFollow(_)        => "Scs Follow"
      case CrcsFollow(_)       => "Crcs Follow"
      case Pwfs1Follow(_)      => "Pwfs1 Follow"
      case Pwfs2Follow(_)      => "Pwfs2 Follow"
      case OiwfsFollow(_)      => "Oiwfs Follow"
      case AowfsFollow(_)      => "Aowfs Follow"
      case Cwfs1Follow(_)      => "Cwfs1 Follow"
      case Cwfs2Follow(_)      => "Cwfs2 Follow"
      case Cwfs3Follow(_)      => "Cwfs3 Follow"
      case Odgw1Follow(_)      => "Odgw1 Follow"
      case Odgw2Follow(_)      => "Odgw2 Follow"
      case Odgw3Follow(_)      => "Odgw3 Follow"
      case Odgw4Follow(_)      => "Odgw4 Follow"
      case McsPark             => "Mcs Park"
      case ScsPark             => "Scs Park"
      case CrcsPark            => "Crcs Park"
      case Pwfs1Park           => "Pwfs1 Park"
      case Pwfs2Park           => "Pwfs2 Park"
      case OiwfsPark           => "Oiwfs Park"
      case AowfsPark           => "Aowfs Park"
      case Cwfs1Park           => "Cwfs1 Park"
      case Cwfs2Park           => "Cwfs2 Park"
      case Cwfs3Park           => "Cwfs3 Park"
      case Odgw1Park           => "Odgw1 Park"
      case Odgw2Park           => "Odgw2 Park"
      case Odgw3Park           => "Odgw3 Park"
      case Odgw4Park           => "Odgw4 Park"
      case CrcsStop(_)         => "Crcs Stop"
      case CrcsMove(_)         => "Crcs Move"
      case _: EcsCarouselMode  => "Ecs Carousel Mode"
      case _: EcsVentGatesMove => "Ecs Vent Gates Move"
    }
  }

}
