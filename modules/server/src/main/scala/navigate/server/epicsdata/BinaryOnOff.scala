// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.epicsdata

import cats.Eq
import lucuma.core.util.Enumerated

abstract class BinaryOnOff(val tag: String) extends Product with Serializable

object BinaryOnOff {
  case object Off extends BinaryOnOff("Off")
  case object On  extends BinaryOnOff("On")

  given Enumerated[BinaryOnOff] = Enumerated.from(Off, On).withTag(_.tag)

  given Eq[BinaryOnOff] = Eq.instance {
    case (Off, Off) => true
    case (On, On)   => true
    case _          => false
  }
}
