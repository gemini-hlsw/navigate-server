// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Eq
import cats.derived.*
import navigate.server.tcs.FollowStatus.NotFollowing
import navigate.server.tcs.ParkStatus.Parked

case class MechSystemState(
  parked:    ParkStatus,
  following: FollowStatus
) derives Eq

object MechSystemState {
  val default: MechSystemState = MechSystemState(Parked, NotFollowing)
}
