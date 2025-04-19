// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

case class TrackingConfig(
  nodAchopA: Boolean,
  nodAchopB: Boolean,
  nodBchopA: Boolean,
  nodBchopB: Boolean
)

object TrackingConfig {
  val default: TrackingConfig    = TrackingConfig(true, false, false, true)
  val noTracking: TrackingConfig = TrackingConfig(false, false, false, false)
}
