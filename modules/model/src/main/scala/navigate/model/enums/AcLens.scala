// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum AcLens(val tag: String) derives Enumerated {
  case Ac    extends AcLens("AC")
  case Hrwfs extends AcLens("HRWFS")
}
