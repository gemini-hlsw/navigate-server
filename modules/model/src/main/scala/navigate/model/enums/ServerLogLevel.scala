// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum ServerLogLevel(val tag: String) extends Product with Serializable derives Enumerated {
  case INFO  extends ServerLogLevel("INFO")
  case WARN  extends ServerLogLevel("WARNING")
  case ERROR extends ServerLogLevel("ERROR")
}
