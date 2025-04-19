// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum DeployableBafflePosition(val tag: String) extends Product with Serializable
    derives Enumerated {
  case ThermalIR extends DeployableBafflePosition("ThermalIR")
  case NearIR    extends DeployableBafflePosition("NearIR")
  case Visible   extends DeployableBafflePosition("Visible")
  case Extended  extends DeployableBafflePosition("Extended")
}
