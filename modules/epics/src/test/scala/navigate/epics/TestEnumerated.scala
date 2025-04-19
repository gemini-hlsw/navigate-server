// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.epics

import lucuma.core.util.Enumerated

enum TestEnumerated(val tag: String) extends Product with Serializable derives Enumerated {
  case VAL0 extends TestEnumerated("val0")
  case VAL1 extends TestEnumerated("val1")
  case VAL2 extends TestEnumerated("val1")
}
