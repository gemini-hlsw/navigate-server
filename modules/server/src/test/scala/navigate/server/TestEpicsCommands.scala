// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.data.State
import cats.syntax.all.*
import monocle.Lens
import navigate.server.tcs.BaseCommand

case class TestEpicsCommands[S, E](cmds: List[State[S, E]]) {
  def post(s0: S): (S, List[E])                      = cmds.sequence.run(s0).value
  def add(c:   State[S, E]): TestEpicsCommands[S, E] = TestEpicsCommands[S, E](cmds :+ c)
}

object TestEpicsCommands {

  trait CommandAccum[S, A] {
    def add(a: A): S
  }

  abstract class TestEpicsCommand0[F[_], +C, S, A](
    l: Lens[C, TestEpicsCommands[S, A]],
    c: C
  ) extends BaseCommand[F, C] {
    override def mark: C = l.modify(cs =>
      cs.add(
        State.modify(cmd).get.map(event)
      )
    )(c)

    protected def event(st: S): A

    protected def cmd(st: S): S
  }

  abstract class TestEpicsCommand1[F[_], +C, S, A, U](
    l: Lens[C, TestEpicsCommands[S, A]],
    c: C
  ) {
    def param1(v: U): C = l.modify(cs =>
      cs.add(
        State.modify(cmd(v)).get.map(event)
      )
    )(c)

    protected def event(st: S): A

    protected def cmd(v: U)(st: S): S
  }

}
