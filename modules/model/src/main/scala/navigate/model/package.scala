// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate

import cats.Eq

import java.util.UUID

package model {
  case class ClientId(self: UUID) extends AnyVal
}
package object model {

  given Eq[ClientId] = Eq.by(x => x.self)

}
