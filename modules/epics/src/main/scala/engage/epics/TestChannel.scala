// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.Applicative
import cats.effect.Concurrent
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all._
import engage.epics.TestChannel.State
import fs2.Stream
import monocle.Lens
import mouse.all._
import org.epics.ca.AccessRights
import org.epics.ca.ConnectionState

import scala.concurrent.duration.FiniteDuration

class TestChannel[F[_]: Applicative, S, A](s: Ref[F, S], l: Lens[S, State[A]])
    extends Channel[F, A] {
  override val get: F[A] = s.get.map(x => l.get(x).value.get)

  override def get(timeout: FiniteDuration): F[A] = get

  override def put(v: A): F[Unit] = s.update(l.modify(x => x.copy(value = v.some)))

  override def valueStream(implicit dispatcher: Dispatcher[F]): Resource[F, Stream[F, A]] =
    Resource.pure(Stream.empty)

  override def connectionStream(implicit
    dispatcher: Dispatcher[F]
  ): Resource[F, Stream[F, Boolean]] =
    Resource.pure(Stream.empty)

  override def eventStream(implicit
    dispatcher: Dispatcher[F],
    concurrent: Concurrent[F]
  ): Resource[F, fs2.Stream[F, Channel.StreamEvent[A]]] =
    Resource.pure(Stream.empty)

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
    def default[A]: State[A] = State(connected = false, none)
  }

}
