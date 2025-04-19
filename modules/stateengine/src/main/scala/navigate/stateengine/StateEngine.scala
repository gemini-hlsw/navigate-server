// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.stateengine

import cats.effect.Concurrent
import cats.effect.std.Queue
import cats.syntax.all.*
import fs2.Stream

trait StateEngine[F[_], S, O] {
  import StateEngine._

  type HandlerType = Handler[F, S, Event[F, S, O], Option[O]]

  def process(s0: S): Stream[F, (S, O)]
  def offer(h:    HandlerType): F[Unit]

  def lift(ff: F[O]): Handler[F, S, Event[F, S, O], Option[O]]

  val getState: Handler[F, S, Event[F, S, O], S] = Handler.get

  def modifyState(ff: S => S): Handler[F, S, Event[F, S, O], Option[O]] =
    Handler.modify[F, S, Event[F, S, O]](ff).map(_ => None)

  def setState(s: S): Handler[F, S, Event[F, S, O], Option[O]] =
    Handler.modify[F, S, Event[F, S, O]]((_: S) => s).map(_ => None)

  def pure(o: O): Handler[F, S, Event[F, S, O], Option[O]] =
    o.some.pure[Handler[F, S, Event[F, S, O], *]]

  val void: Handler[F, S, Event[F, S, O], Option[O]] = Handler.pure(none)

}

object StateEngine {

  case class Event[F[_], S, A](handle: Handler[F, S, Event[F, S, A], Option[A]])

  class StateEngineImpl[F[_]: Concurrent, S, O](
    inputQueue:  Queue[F, Event[F, S, O]],
    streamQueue: Queue[F, Stream[F, Event[F, S, O]]]
  ) extends StateEngine[F, S, O] {
    override def process(s0: S): Stream[F, (S, O)] =
      Stream.exec(streamQueue.offer(Stream.fromQueueUnterminated(inputQueue))) ++
        Stream
          .fromQueueUnterminated(streamQueue)
          .parJoinUnbounded
          .mapAccumulate(s0)((s, i) => i.handle.run.run(s).value)
          .evalMap {
            case (s, Handler.RetVal(o, Some(ss))) => streamQueue.offer(ss).as((s, o))
            case (s, Handler.RetVal(o, None))     => (s, o).pure[F]
          }
          .collect { case (s, Some(o)) => (s, o) }

    override def offer(h: HandlerType): F[Unit] = inputQueue.offer(Event(h))

    override def lift(ff: F[O]): Handler[F, S, Event[F, S, O], Option[O]] = Handler
      .fromStream[F, S, Event[F, S, O]](
        Stream.eval[F, Event[F, S, O]](
          ff.map(x => Event[F, S, O](x.some.pure[Handler[F, S, Event[F, S, O], *]]))
        )
      )
      .map(_ => None)

  }

  def build[F[_]: Concurrent, S, O]: F[StateEngine[F, S, O]] = for {
    sq <- Queue.unbounded[F, Stream[F, Event[F, S, O]]]
    iq <- Queue.unbounded[F, Event[F, S, O]]
  } yield new StateEngineImpl[F, S, O](iq, sq)

}
