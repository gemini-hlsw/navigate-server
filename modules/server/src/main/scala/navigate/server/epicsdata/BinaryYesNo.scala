// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.epicsdata

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class BinaryYesNo(val tag: String) extends Product with Serializable

object BinaryYesNo {
  case object No  extends BinaryYesNo("No")
  case object Yes extends BinaryYesNo("Yes")

  given Enumerated[BinaryYesNo] = Enumerated.from(No, Yes).withTag(_.tag)

  given Eq[BinaryYesNo] = Eq.instance {
    case (No, No)   => true
    case (Yes, Yes) => true
    case _          => false
  }
}
