// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class ParkStatus(val tag: String) extends Product with Serializable

object ParkStatus {
  case object NotParked extends ParkStatus("NotParked")
  case object Parked extends ParkStatus("Parked")

  implicit val eqParkStatus: Eq[ParkStatus] = Eq.instance {
    case (NotParked, NotParked) => true
    case (Parked, Parked) => true
    case _ => false
  }

  implicit val enumParkStatus: Enumerated[ParkStatus] = Enumerated.from(NotParked, Parked).withTag(_.tag)

}
