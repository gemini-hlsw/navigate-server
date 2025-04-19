// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.util.Enumerated

enum ReferenceFrame(val tag: String) derives Enumerated {
  case AzimuthElevation extends ReferenceFrame("AzimuthElevation")
  case XY               extends ReferenceFrame("XY")
  case Instrument       extends ReferenceFrame("Instrument")
  case Tracking         extends ReferenceFrame("Tracking")
}
