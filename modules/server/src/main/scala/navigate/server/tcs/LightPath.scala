// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.enums.LightSinkName
import navigate.model.enums.LightSource

case class LightPath(
  form: LightSource,
  to:   LightSinkName
)
