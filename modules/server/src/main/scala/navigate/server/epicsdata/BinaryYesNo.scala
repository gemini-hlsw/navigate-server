// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.epicsdata

import lucuma.core.util.Enumerated

enum BinaryYesNo(val tag: String) extends Product with Serializable derives Enumerated {
  case No  extends BinaryYesNo("No")
  case Yes extends BinaryYesNo("Yes")
}
