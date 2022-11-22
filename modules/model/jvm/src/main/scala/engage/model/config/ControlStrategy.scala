// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model.config

import cats.syntax.all._
import lucuma.core.util.Enumerated

sealed abstract class ControlStrategy(val tag: String) extends Product with Serializable

object ControlStrategy {
  // System will be fully controlled by Engage
  case object FullControl extends ControlStrategy("full")
  // Engage connects to system, but only to read values
  case object ReadOnly    extends ControlStrategy("readOnly")
  // All system interactions are internally simulated
  case object Simulated   extends ControlStrategy("simulated")

  def fromString(v: String): Option[ControlStrategy] = v match {
    case "full"      => Some(FullControl)
    case "readOnly"  => Some(ReadOnly)
    case "simulated" => Some(Simulated)
    case _           => None
  }

  implicit val ControlStrategyEnumerated: Enumerated[ControlStrategy] =
    Enumerated.from(FullControl, ReadOnly, Simulated).withTag(_.tag)

  implicit class ControlStrategyOps(v: ControlStrategy) {
    val connect: Boolean      = v =!= ControlStrategy.Simulated
    // If connected, then use real values for keywords
    val realKeywords: Boolean = connect
    val command: Boolean      = v === ControlStrategy.FullControl
  }

}
