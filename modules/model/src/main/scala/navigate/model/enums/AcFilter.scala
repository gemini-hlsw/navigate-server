// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum AcFilter(val tag: String) extends Product with Serializable derives Enumerated {
  case Neutral extends AcFilter("Neutral")
  case U_Red1  extends AcFilter("U_Red1")
  case B_Blue  extends AcFilter("B_Blue")
  case V_Green extends AcFilter("V_Green")
  case R_Red2  extends AcFilter("R_Red2")
  case I_Red3  extends AcFilter("I_Red3")
}
