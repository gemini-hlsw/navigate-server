// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum DomeMode(val tag: String) extends Product with Serializable derives Enumerated {
  case Basic        extends DomeMode("Basic")
  case MinScatter   extends DomeMode("MinScatter")
  case MinVibration extends DomeMode("MinVibration")
}
