// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.math.Angle
import navigate.model.Distance

case class InstrumentSpecifics(
  iaa:         Angle,
  focusOffset: Distance,
  agName:      String,
  origin:      Origin
)
