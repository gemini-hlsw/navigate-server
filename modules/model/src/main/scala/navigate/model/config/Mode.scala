// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.config

import lucuma.core.util.Enumerated

/**
 * Operating mode of the navigate, development or production
 */
enum Mode(val tag: String) extends Product with Serializable derives Enumerated {
  case Production  extends Mode("production")
  case Development extends Mode("development")
}
