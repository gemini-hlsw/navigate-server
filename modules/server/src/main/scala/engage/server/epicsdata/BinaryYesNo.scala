// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.epicsdata

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class BinaryYesNo(val tag: String) extends Product with Serializable

object BinaryYesNo {
  case object No  extends BinaryYesNo("no")
  case object Yes extends BinaryYesNo("yes")

  implicit val yesnoEnum: Enumerated[BinaryYesNo] = Enumerated.from(No, Yes).withTag(_.tag)

  implicit val yesnoEq: Eq[BinaryYesNo] = Eq.instance {
    case (No, No)   => true
    case (Yes, Yes) => true
    case _          => false
  }
}
