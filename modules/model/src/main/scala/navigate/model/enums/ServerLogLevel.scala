// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

sealed abstract class ServerLogLevel(val label: String) extends Product with Serializable

object ServerLogLevel {

  case object INFO  extends ServerLogLevel("INFO")
  case object WARN  extends ServerLogLevel("WARNING")
  case object ERROR extends ServerLogLevel("ERROR")

  /** @group Typeclass Instances */
  given Enumerated[ServerLogLevel] =
    Enumerated.from(INFO, WARN, ERROR).withTag(_.label)
}
