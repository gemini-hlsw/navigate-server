// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.acm

import lucuma.core.util.Enumerated

enum CadDirective(val tag: String) extends Product with Serializable derives Enumerated {
  case MARK   extends CadDirective("mark")
  case CLEAR  extends CadDirective("clear")
  case PRESET extends CadDirective("preset")
  case START  extends CadDirective("start")
  case STOP   extends CadDirective("stop")
}
