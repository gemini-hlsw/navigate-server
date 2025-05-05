// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.util.Enumerated

enum RotatorTrackingMode(val tag: String) extends Product with Serializable derives Enumerated {
  case Tracking extends RotatorTrackingMode("Tracking")
  case Fixed    extends RotatorTrackingMode("Fixed")
}
