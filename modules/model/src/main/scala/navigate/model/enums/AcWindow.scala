// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum AcWindow(val tag: String) derives Enumerated {
  case Full      extends AcWindow("full")
  case Square100 extends AcWindow("100x100")
  case Square200 extends AcWindow("200x200")
}
