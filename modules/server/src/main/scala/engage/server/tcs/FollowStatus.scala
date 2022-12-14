// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class FollowStatus(val tag: String) extends Product with Serializable

object FollowStatus {
  case object NotFollowing extends FollowStatus("NotFollowing")
  case object Following extends FollowStatus("Following")

  implicit val eqFollowStatus: Eq[FollowStatus] = Eq.instance{
    case (NotFollowing, NotFollowing) => true
    case (Following, Following) => true
    case _ => false
  }
  
  implicit val enumFollowStatus: Enumerated[FollowStatus] = Enumerated.from[FollowStatus](NotFollowing, Following).withTag(_.tag)
}
