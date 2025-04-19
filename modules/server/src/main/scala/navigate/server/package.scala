// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate

import scala.concurrent.duration.*

package object server {
  val ConnectionTimeout: FiniteDuration = FiniteDuration.apply(1, SECONDS)
}
