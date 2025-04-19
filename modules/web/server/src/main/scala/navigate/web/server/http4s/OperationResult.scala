// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import lucuma.core.util.Enumerated

enum OperationResult(val tag: String) extends Product with Serializable derives Enumerated {
  case Success extends OperationResult("Success")
  case Failure extends OperationResult("Failure")
}
