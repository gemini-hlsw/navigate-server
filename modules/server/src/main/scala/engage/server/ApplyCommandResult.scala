// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server

import lucuma.core.util.Enumerated

sealed abstract class ApplyCommandResult(val tag: String) extends Product with Serializable

object ApplyCommandResult {
  case object Paused    extends ApplyCommandResult("paused")
  case object Completed extends ApplyCommandResult("completed")

  /** @group Typeclass Instances */
  implicit val ApplyCommandResultEnumerated: Enumerated[ApplyCommandResult] =
    Enumerated.from(Paused, Completed).withTag(_.tag)
}
