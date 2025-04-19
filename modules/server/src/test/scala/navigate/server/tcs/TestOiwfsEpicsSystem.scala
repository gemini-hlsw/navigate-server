// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Parallel
import cats.effect.Ref
import cats.effect.Temporal
import monocle.Focus
import navigate.epics.TestChannel
import navigate.server.acm.CadDirective

object TestOiwfsEpicsSystem {
  case class State(
    detSigModeSeqDarkDir: TestChannel.State[CadDirective],
    seqDarkFilename:      TestChannel.State[String],
    detSigModeSeqDir:     TestChannel.State[CadDirective],
    z2m2:                 TestChannel.State[String],
    detSigInitDir:        TestChannel.State[CadDirective],
    darkFilename:         TestChannel.State[String]
  )

  val defaultState: State = State(
    TestChannel.State.default,
    TestChannel.State.default,
    TestChannel.State.default,
    TestChannel.State.default,
    TestChannel.State.default,
    TestChannel.State.default
  )

  def buildChannels[F[_]: Temporal](s: Ref[F, State]): OiwfsChannels[F] = OiwfsChannels(
    detSigModeSeqDarkDir = TestChannel(s, Focus[State](_.detSigModeSeqDarkDir)),
    seqDarkFilename = TestChannel(s, Focus[State](_.seqDarkFilename)),
    detSigModeSeqDir = TestChannel(s, Focus[State](_.detSigModeSeqDir)),
    z2m2 = TestChannel(s, Focus[State](_.z2m2)),
    detSigInitDir = TestChannel(s, Focus[State](_.detSigInitDir)),
    darkFilename = TestChannel(s, Focus[State](_.darkFilename))
  )

  def build[F[_]: {Temporal, Parallel}](
    wfs:   Ref[F, TestWfsEpicsSystem.State],
    oiwfs: Ref[F, State]
  ): OiwfsEpicsSystem[F] =
    OiwfsEpicsSystem.buildSystem(TestWfsEpicsSystem.buildChannels("OIWFS", wfs),
                                 buildChannels(oiwfs)
    )

}
