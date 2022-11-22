// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server

import cats.syntax.all._
import engage.model.ClientId
import engage.model.enums.DomeMode
import engage.model.enums.ShutterMode
import lucuma.core.util.Enumerated

package object http4s {
  object ClientIDVar {
    def unapply(str: String): Option[ClientId] =
      Either.catchNonFatal(ClientId(java.util.UUID.fromString(str))).toOption
  }

  object BooleanVar {
    def unapply(str: String): Option[Boolean] =
      Either.catchNonFatal(str.toBoolean).toOption
  }

  object DoubleVar {
    def unapply(str: String): Option[Double] =
      Either.catchNonFatal(str.toDouble).toOption
  }

  object DomeModeVar {
    def unapply(str: String): Option[DomeMode] =
      Enumerated[DomeMode].fromTag(str)
  }

  object ShutterModeVar {
    def unapply(str: String): Option[ShutterMode] =
      Enumerated[ShutterMode].fromTag(str)
  }

}
