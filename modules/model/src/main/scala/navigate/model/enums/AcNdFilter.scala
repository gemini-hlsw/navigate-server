// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum AcNdFilter(val tag: String) derives Enumerated {
  case Nd3    extends AcNdFilter("ND3")
  case Nd2    extends AcNdFilter("ND2")
  case Nd1    extends AcNdFilter("ND1")
  case Nd100  extends AcNdFilter("nd100")
  case Nd1000 extends AcNdFilter("nd1000")
  case Filt04 extends AcNdFilter("filt04")
  case Filt06 extends AcNdFilter("filt06")
  case Filt08 extends AcNdFilter("filt08")
}
