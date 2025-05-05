// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

case class GuidersQualityValues(
  pwfs1: GuidersQualityValues.GuiderQuality,
  pwfs2: GuidersQualityValues.GuiderQuality,
  oiwfs: GuidersQualityValues.GuiderQuality
)

object GuidersQualityValues {

  case class GuiderQuality(
    flux:             Int,
    centroidDetected: Boolean
  )

}
