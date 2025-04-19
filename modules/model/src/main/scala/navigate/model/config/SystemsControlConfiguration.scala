// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.config

import cats.*
import cats.derived.*

/**
 * Indicates how each subsystems is treated, e.g. full connection or simulated
 */
case class SystemsControlConfiguration(
  altair:  ControlStrategy,
  gems:    ControlStrategy,
  gcal:    ControlStrategy,
  gpi:     ControlStrategy,
  gsaoi:   ControlStrategy,
  tcs:     ControlStrategy,
  observe: ControlStrategy
) derives Eq {
  def connectEpics: Boolean =
    altair.connect || gems.connect || gcal.connect || tcs.connect
}
