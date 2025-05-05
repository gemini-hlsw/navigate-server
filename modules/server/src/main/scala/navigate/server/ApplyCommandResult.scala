// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import lucuma.core.util.Enumerated

enum ApplyCommandResult(val tag: String) extends Product with Serializable derives Enumerated {
  case Paused    extends ApplyCommandResult("paused")
  case Completed extends ApplyCommandResult("completed")
}
