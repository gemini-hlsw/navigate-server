// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.acm

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class CarState(val tag: String) extends Product with Serializable

object CarState {
  case object Idle   extends CarState("idle")
  case object Paused extends CarState("paused")
  case object Busy   extends CarState("busy")
  case object Error  extends CarState("error")

  implicit val carStateEq: Eq[CarState] = Eq.fromUniversalEquals

  implicit val carStateEnum: Enumerated[CarState] = Enumerated.from(Idle, Paused, Busy, Idle).withTag(_.tag)
}
