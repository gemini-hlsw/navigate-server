// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Eq
import lucuma.core.math.Angle

case class RotatorTrackConfig (
  ipa: Angle,
  mode: RotatorTrackingMode
)
