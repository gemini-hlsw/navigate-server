// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Eq
import cats.derived.*

case class TelescopeState(
  mount: MechSystemState,
  scs:   MechSystemState,
  crcs:  MechSystemState,
  pwfs1: MechSystemState,
  pwfs2: MechSystemState,
  oiwfs: MechSystemState
) derives Eq

object TelescopeState {
  val default: TelescopeState = TelescopeState(
    mount = MechSystemState.default,
    scs = MechSystemState.default,
    crcs = MechSystemState.default,
    pwfs1 = MechSystemState.default,
    pwfs2 = MechSystemState.default,
    oiwfs = MechSystemState.default
  )
}
