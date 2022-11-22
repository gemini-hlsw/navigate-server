// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.model

import cats.Eq

object Pages {

  sealed trait EngagePages extends Product with Serializable

  case object Root extends EngagePages

  implicit val engagePagesEq: Eq[EngagePages] = Eq.instance { case (Root, Root) =>
    true
  }
}
