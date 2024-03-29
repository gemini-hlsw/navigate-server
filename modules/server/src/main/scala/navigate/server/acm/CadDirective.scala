// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.acm

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class CadDirective(val tag: String) extends Product with Serializable

object CadDirective {
  case object MARK   extends CadDirective("mark")
  case object CLEAR  extends CadDirective("clear")
  case object PRESET extends CadDirective("preset")
  case object START  extends CadDirective("start")
  case object STOP   extends CadDirective("stop")

  given Eq[CadDirective] = Eq.fromUniversalEquals

  given Enumerated[CadDirective] =
    Enumerated.from(MARK, CLEAR, PRESET, START, STOP).withTag(_.tag)
}
