// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.Monad
import cats.Parallel
import cats.effect.Ref
import cats.effect.Temporal
import eu.timepit.refined.types.string.NonEmptyString
import monocle.Focus
import navigate.epics.EpicsSystem
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.TestChannel
import navigate.server.acm.CadDirective

object TestWfsEpicsSystem {
  case class State(
    telltale:  TestChannel.State[String],
    tipGain:   TestChannel.State[String],
    tiltGain:  TestChannel.State[String],
    focusGain: TestChannel.State[String],
    reset:     TestChannel.State[Double],
    gainsDir:  TestChannel.State[CadDirective],
    flux:      TestChannel.State[Int],
    centroid:  TestChannel.State[Int]
  )

  val defaultState: State = State(
    TestChannel.State.default,
    TestChannel.State.default,
    TestChannel.State.default,
    TestChannel.State.default,
    TestChannel.State.default,
    TestChannel.State.default,
    TestChannel.State.default,
    TestChannel.State.default
  )

  def buildChannels[F[_]: Applicative](
    sysName: String,
    top:     NonEmptyString,
    s:       Ref[F, State]
  ): WfsChannels[F] = WfsChannels(
    telltale =
      TelltaleChannel[F]("sysName", new TestChannel[F, State, String](s, Focus[State](_.telltale))),
    tipGain = new TestChannel[F, State, String](s, Focus[State](_.tipGain)),
    tiltGain = new TestChannel[F, State, String](s, Focus[State](_.tiltGain)),
    focusGain = new TestChannel[F, State, String](s, Focus[State](_.focusGain)),
    reset = new TestChannel[F, State, Double](s, Focus[State](_.reset)),
    gainsDir = new TestChannel[F, State, CadDirective](s, Focus[State](_.gainsDir)),
    flux = new TestChannel[F, State, Int](s, Focus[State](_.flux)),
    centroidDetected = new TestChannel[F, State, Int](s, Focus[State](_.centroid))
  )

  def build[F[_]: Monad: Temporal: Parallel](
    sysName: String,
    top:     NonEmptyString,
    s:       Ref[F, State]
  ): WfsEpicsSystem[F] = WfsEpicsSystem.buildSystem(buildChannels(sysName, top, s))

}
