// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum AcquisitionAdjustmentCommand(val tag: String) derives Enumerated:
  case AskUser      extends AcquisitionAdjustmentCommand("ASK_USER")
  case UserConfirms extends AcquisitionAdjustmentCommand("USER_CONFIRMS")
  case UserCancels  extends AcquisitionAdjustmentCommand("USER_CANCELS")
