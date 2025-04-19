// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.math.Angle
import lucuma.core.math.Coordinates
import lucuma.core.math.Epoch
import lucuma.core.math.Parallax
import lucuma.core.math.ProperMotion
import lucuma.core.math.RadialVelocity
import lucuma.core.math.Wavelength
import monocle.Lens

sealed trait Target extends Product with Serializable {
  val objectName: String
  val wavelength: Option[Wavelength]
}

object Target {
  case class SiderealTarget(
    override val objectName: String,
    override val wavelength: Option[Wavelength],
    coordinates:             Coordinates,
    epoch:                   Epoch,
    properMotion:            Option[ProperMotion],
    radialVelocity:          Option[RadialVelocity],
    parallax:                Option[Parallax]
  ) extends Target

  case class Azimuth(toAngle: Angle)

  case class Elevation(toAngle: Angle)

  case class AzElCoordinates(azimuth: Azimuth, elevation: Elevation)

  case class AzElTarget(
    override val objectName: String,
    override val wavelength: Option[Wavelength],
    coordinates:             AzElCoordinates
  ) extends Target

  case class EphemerisTarget(
    override val objectName: String,
    override val wavelength: Option[Wavelength],
    ephemerisFile:           String
  ) extends Target

  val objectName: Lens[Target, String] = Lens.apply[Target, String](_.objectName) { s =>
    {
      case a: SiderealTarget  => a.copy(objectName = s)
      case a: AzElTarget      => a.copy(objectName = s)
      case a: EphemerisTarget => a.copy(objectName = s)
    }
  }

  val wavelength: Lens[Target, Option[Wavelength]] =
    Lens.apply[Target, Option[Wavelength]](_.wavelength) { s =>
      {
        case a: SiderealTarget  => a.copy(wavelength = s)
        case a: AzElTarget      => a.copy(wavelength = s)
        case a: EphemerisTarget => a.copy(wavelength = s)
      }
    }

}
