// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.util.Enumerated

enum FollowStatus(val tag: String) extends Product with Serializable derives Enumerated {
  case NotFollowing extends FollowStatus("NotFollowing")
  case Following    extends FollowStatus("Following")
}
