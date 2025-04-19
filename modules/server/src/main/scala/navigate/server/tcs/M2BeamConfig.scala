// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.util.Enumerated

enum M2BeamConfig(val tag: String) derives Enumerated {
  case None   extends M2BeamConfig("None")
  case BeamA  extends M2BeamConfig("A")
  case BeamB  extends M2BeamConfig("B")
  case BeamAB extends M2BeamConfig("AB")
}

object M2BeamConfig {
  def fromTcsGuideConfig(str: String): M2BeamConfig = {
    val sets = str.split(" ").toList
    if (sets.contains("A-AUTO") && sets.contains("B-AUTO")) BeamAB
    else if (sets.contains("A-AUTO")) BeamA
    else if (sets.contains("B-AUTO")) BeamB
    else None
  }
}
