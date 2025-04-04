// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.Applicative
import cats.syntax.all.*

trait OdbProxy[F[_]] {}

object OdbProxy {
  def build[F[_]: Applicative]: F[OdbProxy[F]] = new OdbProxy[F] {}.pure[F]
  def dummy[F[_]: Applicative]: OdbProxy[F]    = new OdbProxy[F] {}
}
