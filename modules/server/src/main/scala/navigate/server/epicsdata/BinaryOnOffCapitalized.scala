// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.epicsdata

import lucuma.core.util.Enumerated

enum BinaryOnOffCapitalized(val tag: String) derives Enumerated {
  case On  extends BinaryOnOffCapitalized("ON")
  case Off extends BinaryOnOffCapitalized("OFF")
}
