// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.epics

import cats.Eq
import cats.effect.Concurrent
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.Temporal
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import fs2.Stream
import monocle.Lens
import mouse.all.*
import navigate.epics.TestChannel.State
import org.epics.ca.AccessRights
import org.epics.ca.ConnectionState

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class TestChannel[F[_]: Temporal, S, A: Eq](s: Ref[F, S], l: Lens[S, State[A]])
    extends Channel[F, A] {
  override val get: F[A] = s.get.map(x => l.get(x).value.get)

  override def get(timeout: FiniteDuration): F[A] = get

  override def put(v: A): F[Unit] = s.update(l.modify(x => x.copy(value = v.some)))

  private def pollingT                                                                 = FiniteDuration(100, TimeUnit.MILLISECONDS)
  override def valueStream(using dispatcher: Dispatcher[F]): Resource[F, Stream[F, A]] =
    Resource.pure(
      Stream
        .fixedRate(pollingT)
        .zipRight(Stream.eval(s.get.map(x => l.get(x).value)).repeat)
        .flattenOption
        .mapAccumulate(none[A]) { case (acc, v) =>
          (v.some, acc.forall(_ =!= v).option(v))
        }
        .map(_._2)
        .flattenOption
    )

  override def connectionStream(using
    dispatcher: Dispatcher[F]
  ): Resource[F, Stream[F, Boolean]] =
    Resource.pure(
      Stream
        .fixedRate(pollingT)
        .zipRight(Stream.eval(s.get.map(x => l.get(x).connected)).repeat)
        .mapAccumulate(none[Boolean]) { case (acc, v) =>
          (v.some, acc.forall(_ =!= v).option(v))
        }
        .map(_._2)
        .flattenOption
    )

  override def eventStream(using
    dispatcher: Dispatcher[F],
    concurrent: Concurrent[F]
  ): Resource[F, fs2.Stream[F, Channel.StreamEvent[A]]] = for {
    vs <- valueStream
    cs <- connectionStream
  } yield vs
    .map(Channel.StreamEvent.ValueChanged[A])
    .merge(cs.map(_.fold(Channel.StreamEvent.Connected, Channel.StreamEvent.Disconnected)))

  override def connect: F[Unit] = s.update(l.modify(_.copy(connected = true)))

  override def connect(timeout: FiniteDuration): F[Unit] = connect

  override def disconnect: F[Unit] = s.update(l.modify(_.copy(connected = true)))

  override def getName: F[String] = "".pure[F]

  override def getConnectionState: F[ConnectionState] =
    s.get.map(l.get(_).connected.fold(ConnectionState.CONNECTED, ConnectionState.DISCONNECTED))

  override def getAccessRights: F[AccessRights] = AccessRights.READ_WRITE.pure[F]

}

object TestChannel {
  case class State[A](connected: Boolean, value: Option[A])

  object State {
    def of[A](v: A): State[A] = State(connected = false, v.some)
    def default[A]: State[A]  = State(connected = false, none)
  }

}
