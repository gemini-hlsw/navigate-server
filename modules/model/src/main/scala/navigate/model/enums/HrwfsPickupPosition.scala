// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum HrwfsPickupPosition(val tag: String) extends Product with Serializable derives Enumerated {
  case Out extends HrwfsPickupPosition("OUT")
  case In  extends HrwfsPickupPosition("IN")
}
