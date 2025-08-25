// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.math.Wavelength

enum OiwfsWavelength(val tag: String, val wavel: Wavelength) {
  case GmosOiwfs extends OiwfsWavelength("GmosOiwfs", Wavelength.unsafeFromIntPicometers(650_000))
  case Flamingos2Oiwfs
      extends OiwfsWavelength("Flamingos2Oiwfs", Wavelength.unsafeFromIntPicometers(650_000))
  case H         extends OiwfsWavelength("H", Wavelength.unsafeFromIntPicometers(1_650_000))
  case J         extends OiwfsWavelength("J", Wavelength.unsafeFromIntPicometers(1_250_000))
  case K         extends OiwfsWavelength("K", Wavelength.unsafeFromIntPicometers(2_200_000))
  case KPrime    extends OiwfsWavelength("KPrime", Wavelength.unsafeFromIntPicometers(2_120_000))
}
