// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model

case class NavigateState(
  onSwappedTarget: Boolean
)

object NavigateState {
  val default: NavigateState = NavigateState(onSwappedTarget = false)
}
