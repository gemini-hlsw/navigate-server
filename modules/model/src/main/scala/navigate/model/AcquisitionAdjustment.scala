// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model

import cats.Eq
import cats.derived.*
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import navigate.model.enums.AcquisitionAdjustmentCommand

case class AcquisitionAdjustment(
  offset:  Offset,
  ipa:     Option[Angle],
  iaa:     Option[Angle],
  command: AcquisitionAdjustmentCommand = AcquisitionAdjustmentCommand.AskUser
) derives Eq
