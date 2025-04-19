// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.epicsdata

import lucuma.core.util.Enumerated

enum BinaryOnOff(val tag: String) extends Product with Serializable derives Enumerated {
  case Off extends BinaryOnOff("Off")
  case On  extends BinaryOnOff("On")
}
