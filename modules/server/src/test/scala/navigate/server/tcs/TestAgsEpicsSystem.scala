// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Parallel
import cats.effect.Ref
import cats.effect.Temporal
import monocle.Focus
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.TestChannel
import navigate.server.tcs.AgsChannels.InstrumentPortChannels

object TestAgsEpicsSystem {

  case class State(
    telltale:   TestChannel.State[String],
    inPosition: TestChannel.State[Int],
    sfParked:   TestChannel.State[Int],
    aoParked:   TestChannel.State[Int],
    p1Parked:   TestChannel.State[Int],
    p1Follow:   TestChannel.State[String],
    p2Parked:   TestChannel.State[Int],
    p2Follow:   TestChannel.State[String],
    oiParked:   TestChannel.State[Int],
    oiFollow:   TestChannel.State[String],
    f2Port:     TestChannel.State[Int],
    ghostPort:  TestChannel.State[Int],
    gmosPort:   TestChannel.State[Int],
    gnirsPort:  TestChannel.State[Int],
    gpiPort:    TestChannel.State[Int],
    gsaoiPort:  TestChannel.State[Int],
    nifsPort:   TestChannel.State[Int],
    niriPort:   TestChannel.State[Int]
  )

  val defaultState: State = State(
    TestChannel.State.of(""),
    TestChannel.State.of(0),
    TestChannel.State.of(1),
    TestChannel.State.of(1),
    TestChannel.State.of(1),
    TestChannel.State.of(""),
    TestChannel.State.of(1),
    TestChannel.State.of(""),
    TestChannel.State.of(1),
    TestChannel.State.of(""),
    TestChannel.State.of(1),
    TestChannel.State.of(3),
    TestChannel.State.of(0),
    TestChannel.State.of(0),
    TestChannel.State.of(5),
    TestChannel.State.of(0),
    TestChannel.State.of(0),
    TestChannel.State.of(0)
  )

  def buildChannels[F[_]: Temporal](
    s: Ref[F, State]
  ): AgsChannels[F] = new AgsChannels[F](
    telltale =
      TelltaleChannel[F]("AGS", new TestChannel[F, State, String](s, Focus[State](_.telltale))),
    inPosition = new TestChannel[F, State, Int](s, Focus[State](_.inPosition)),
    sfParked = new TestChannel[F, State, Int](s, Focus[State](_.sfParked)),
    aoParked = new TestChannel[F, State, Int](s, Focus[State](_.aoParked)),
    p1Parked = new TestChannel[F, State, Int](s, Focus[State](_.p1Parked)),
    p1Follow = new TestChannel[F, State, String](s, Focus[State](_.p1Follow)),
    p2Parked = new TestChannel[F, State, Int](s, Focus[State](_.p2Parked)),
    p2Follow = new TestChannel[F, State, String](s, Focus[State](_.p2Follow)),
    oiParked = new TestChannel[F, State, Int](s, Focus[State](_.oiParked)),
    oiFollow = new TestChannel[F, State, String](s, Focus[State](_.oiFollow)),
    instrumentPorts = InstrumentPortChannels[F](
      gmos = new TestChannel[F, State, Int](s, Focus[State](_.gmosPort)),
      gsaoi = new TestChannel[F, State, Int](s, Focus[State](_.gsaoiPort)),
      gpi = new TestChannel[F, State, Int](s, Focus[State](_.gpiPort)),
      f2 = new TestChannel[F, State, Int](s, Focus[State](_.f2Port)),
      niri = new TestChannel[F, State, Int](s, Focus[State](_.niriPort)),
      gnirs = new TestChannel[F, State, Int](s, Focus[State](_.gnirsPort)),
      nifs = new TestChannel[F, State, Int](s, Focus[State](_.nifsPort)),
      ghost = new TestChannel[F, State, Int](s, Focus[State](_.ghostPort))
    )
  )

  def build[F[_]: {Temporal, Parallel}](
    s: Ref[F, State]
  ): AgsEpicsSystem[F] = AgsEpicsSystem.buildSystem(buildChannels(s))
}
