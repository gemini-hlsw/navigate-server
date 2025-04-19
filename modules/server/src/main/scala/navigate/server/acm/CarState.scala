// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.acm

import lucuma.core.util.Enumerated

enum CarState(val tag: String) extends Product with Serializable derives Enumerated {
  case IDLE   extends CarState("idle")
  case PAUSED extends CarState("paused")
  case BUSY   extends CarState("busy")
  case ERROR  extends CarState("error")
}
