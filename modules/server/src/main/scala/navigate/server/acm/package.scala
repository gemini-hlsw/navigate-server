// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.Monad
import cats.syntax.all.*
import navigate.epics.Channel
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.VerifiedEpics.VerifiedEpics
import navigate.epics.VerifiedEpics.writeChannel

package object acm {

  def writeCadParam[F[_]: Monad, A](
    tt: TelltaleChannel[F],
    ch: Channel[F, String]
  )(v: A)(using enc: Encoder[A, String]): VerifiedEpics[F, F, Unit] =
    writeChannel[F, String](tt, ch)(enc.encode(v).pure[F])

}
