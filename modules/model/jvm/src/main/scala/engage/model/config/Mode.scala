// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model.config

import lucuma.core.util.Enumerated

/**
 * Operating mode of the engage, development or production
 */
sealed abstract class Mode(val tag: String) extends Product with Serializable

object Mode {
  case object Production  extends Mode("production")
  case object Development extends Mode("development")

  implicit val ModeEnumerated: Enumerated[Mode] =
    Enumerated.from(Production, Development).withTag(_.tag)

}
