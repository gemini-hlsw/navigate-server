// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum AoFoldPosition(val tag: String) extends Product with Serializable derives Enumerated {
  case Out extends AoFoldPosition("OUT")
  case In  extends AoFoldPosition("IN")
}
