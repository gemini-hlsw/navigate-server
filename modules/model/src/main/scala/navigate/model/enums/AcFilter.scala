// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum AcFilter(val tag: String) derives Enumerated {
  case Neutral extends AcFilter("neutral")
  case U_Red1  extends AcFilter("U-red1")
  case B_Blue  extends AcFilter("B-blue")
  case V_Green extends AcFilter("V-green")
  case R_Red2  extends AcFilter("R-red2")
  case I_Red3  extends AcFilter("I-red3")
}
