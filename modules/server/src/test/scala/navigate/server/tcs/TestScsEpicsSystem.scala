// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Parallel
import cats.effect.Ref
import cats.effect.Temporal
import monocle.Focus
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.TestChannel

object TestScsEpicsSystem {

  case class State(
    telltale: TestChannel.State[String],
    follow:   TestChannel.State[String]
  )

  val defaultState: State = State(
    TestChannel.State.of(""),
    TestChannel.State.of("")
  )

  def buildChannels[F[_]: Temporal](
    s: Ref[F, State]
  ): ScsChannels[F] = new ScsChannels[F](
    telltale =
      TelltaleChannel[F]("SCS", new TestChannel[F, State, String](s, Focus[State](_.telltale))),
    follow = new TestChannel[F, State, String](s, Focus[State](_.follow))
  )

  def build[F[_]: {Temporal, Parallel}](
    s: Ref[F, State]
  ): ScsEpicsSystem[F] = ScsEpicsSystem.buildSystem(buildChannels(s))
}
