// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.epicsdata

import lucuma.core.util.Enumerated

enum NodState(val tag: String) derives Enumerated {
  case A extends NodState("A")
  case B extends NodState("B")
  case C extends NodState("C")
}
