// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Parallel
import cats.effect.Ref
import cats.effect.Temporal
import monocle.Focus
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.TestChannel
import navigate.epics.VerifiedEpics
import navigate.server.ApplyCommandResult
import navigate.server.acm.CadDirective
import navigate.server.acm.CarState
import navigate.server.acm.GeminiApplyCommand

import scala.concurrent.duration.FiniteDuration

object TestAcquisitionCameraEpicsSystem {
  case class State(
    telltale:          TestChannel.State[String],
    filterReadout:     TestChannel.State[String],
    lens:              TestChannel.State[String],
    ndFilter:          TestChannel.State[String],
    filter:            TestChannel.State[String],
    frameCount:        TestChannel.State[String],
    expTime:           TestChannel.State[String],
    output:            TestChannel.State[String],
    directory:         TestChannel.State[String],
    fileName:          TestChannel.State[String],
    simFile:           TestChannel.State[String],
    dhsStream:         TestChannel.State[String],
    dhsOption:         TestChannel.State[String],
    obsType:           TestChannel.State[String],
    binning:           TestChannel.State[String],
    windowing:         TestChannel.State[String],
    centerX:           TestChannel.State[String],
    centerY:           TestChannel.State[String],
    width:             TestChannel.State[String],
    height:            TestChannel.State[String],
    dhsLabel:          TestChannel.State[String],
    stopDir:           TestChannel.State[CadDirective],
    observeInProgress: TestChannel.State[CarState]
  )

  val defaultState: State = State(
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(""),
    TestChannel.State.of(CadDirective.CLEAR),
    TestChannel.State.of(CarState.IDLE)
  )

  def buildChannels[F[_]: Temporal](
    s: Ref[F, State]
  ): AcquisitionCameraChannels[F] = new AcquisitionCameraChannels[F](
    telltale =
      TelltaleChannel[F]("AC/HR", new TestChannel[F, State, String](s, Focus[State](_.telltale))),
    filterReadout = new TestChannel[F, State, String](s, Focus[State](_.filterReadout)),
    lens = new TestChannel[F, State, String](s, Focus[State](_.lens)),
    ndFilter = new TestChannel[F, State, String](s, Focus[State](_.ndFilter)),
    filter = new TestChannel[F, State, String](s, Focus[State](_.filter)),
    frameCount = new TestChannel[F, State, String](s, Focus[State](_.frameCount)),
    expTime = new TestChannel[F, State, String](s, Focus[State](_.expTime)),
    output = new TestChannel[F, State, String](s, Focus[State](_.output)),
    directory = new TestChannel[F, State, String](s, Focus[State](_.directory)),
    fileName = new TestChannel[F, State, String](s, Focus[State](_.fileName)),
    simFile = new TestChannel[F, State, String](s, Focus[State](_.simFile)),
    dhsStream = new TestChannel[F, State, String](s, Focus[State](_.dhsStream)),
    dhsOption = new TestChannel[F, State, String](s, Focus[State](_.dhsOption)),
    obsType = new TestChannel[F, State, String](s, Focus[State](_.obsType)),
    binning = new TestChannel[F, State, String](s, Focus[State](_.binning)),
    windowing = new TestChannel[F, State, String](s, Focus[State](_.windowing)),
    centerX = new TestChannel[F, State, String](s, Focus[State](_.centerX)),
    centerY = new TestChannel[F, State, String](s, Focus[State](_.centerY)),
    width = new TestChannel[F, State, String](s, Focus[State](_.width)),
    height = new TestChannel[F, State, String](s, Focus[State](_.height)),
    dhsLabel = new TestChannel[F, State, String](s, Focus[State](_.dhsLabel)),
    stopDir = new TestChannel[F, State, CadDirective](s, Focus[State](_.stopDir)),
    observeInProgress = new TestChannel[F, State, CarState](s, Focus[State](_.observeInProgress))
  )

  def build[F[_]: {Temporal, Parallel}](
    s: Ref[F, State]
  ): AcquisitionCameraEpicsSystem[F] = AcquisitionCameraEpicsSystem.buildSystem(
    (timeout: FiniteDuration) =>
      VerifiedEpics.pureF[F, F, ApplyCommandResult](ApplyCommandResult.Completed),
    buildChannels(s)
  )

}
