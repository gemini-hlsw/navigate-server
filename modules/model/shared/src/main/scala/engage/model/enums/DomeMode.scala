// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model.enums

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class DomeMode(val tag: String) extends Product with Serializable

object DomeMode {
  case object Basic        extends DomeMode("basic")
  case object MinScatter   extends DomeMode("minScatter")
  case object MinVibration extends DomeMode("minVibration")

  implicit val domeModeEq: Eq[DomeMode] = Eq.instance {
    case (Basic, Basic)               => true
    case (MinScatter, MinScatter)     => true
    case (MinVibration, MinVibration) => true
    case _                            => false
  }

  implicit val domeModeEnum: Enumerated[DomeMode] =
    Enumerated.from(Basic, MinScatter, MinVibration).withTag(_.tag)
}
