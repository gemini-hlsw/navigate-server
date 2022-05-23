// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.Eq
import cats.effect.{ Async, Concurrent, Resource }
import cats.effect.std.{ Dispatcher, Queue }
import cats.implicits._
import cats.effect.implicits._
import engage.epics.Channel.StreamEvent
import fs2.Stream
import mouse.all._
import org.epics.ca.{ Channel => CaChannel, Severity, Status }

import scala.concurrent.duration.FiniteDuration
import java.lang.{ Boolean => JBoolean }

abstract class Channel[F[_], T] extends RemoteChannel {
  val get: F[T]
  def get(timeout:                          FiniteDuration): F[T]
  def put(v:                                T): F[Unit]
  def valueStream(implicit dispatcher:      Dispatcher[F]): Resource[F, Stream[F, T]]
  def connectionStream(implicit dispatcher: Dispatcher[F]): Resource[F, Stream[F, Boolean]]
  def eventStream(implicit
    dispatcher:                             Dispatcher[F],
    concurrent:                             Concurrent[F]
  ): Resource[F, Stream[F, StreamEvent[T]]]
}

object Channel {

  sealed trait StreamEvent[+T]

  object StreamEvent {
    case object Connected            extends StreamEvent[Nothing]
    case object Disconnected         extends StreamEvent[Nothing]
    case class ValueChanged[T](v: T) extends StreamEvent[T]

    implicit def streamEventEq[T: Eq]: Eq[StreamEvent[T]] = Eq.instance {
      case (Connected, Connected)             => true
      case (Disconnected, Disconnected)       => true
      case (ValueChanged(a), ValueChanged(b)) => a === b
      case _                                  => false
    }

  }

  private final class ChannelImpl[F[_]: Async, T, J](override val caChannel: CaChannel[J])(implicit
    cv:                                                                      Convert[T, J]
  ) extends Channel[F, T] {
    override val get: F[T]                          =
      Async[F]
        .fromCompletableFuture(Async[F].delay(caChannel.getAsync()))
        .flatMap(x =>
          cv.fromJava(x)
            .map(_.pure[F])
            .getOrElse(Async[F].raiseError(new Throwable(Status.NOCONVERT.getMessage)))
        )
    override def get(timeout: FiniteDuration): F[T] = get.timeout(timeout)
    override def put(v: T): F[Unit]                 = cv
      .toJava(v)
      .map(a => Async[F].fromCompletableFuture(Async[F].delay(caChannel.putAsync(a))))
      .getOrElse(Status.NOCONVERT.pure[F])
      .flatMap { s =>
        if (s.getSeverity() == Severity.SUCCESS) Async[F].unit
        else Async[F].raiseError(new Throwable(s.getMessage()))
      }

    override def valueStream(implicit dispatcher: Dispatcher[F]): Resource[F, Stream[F, T]] = for {
      q <- Resource.eval(Queue.unbounded[F, T])
      _ <- Resource.make {
             Async[F].delay(
               caChannel.addValueMonitor { (v: J) =>
                 cv.fromJava(v).foreach(x => dispatcher.unsafeRunAndForget(q.offer(x)))
                 ()
               }
             )
           }(x => Async[F].delay(x.close()))
      s <- Resource.pure(Stream.fromQueueUnterminated(q))
    } yield s

    override def connectionStream(implicit
      dispatcher: Dispatcher[F]
    ): Resource[F, Stream[F, Boolean]] = for {
      q <- Resource.eval(Queue.unbounded[F, Boolean])
      _ <- Resource.make {
             Async[F].delay(
               caChannel.addConnectionListener((_: CaChannel[J], c: JBoolean) =>
                 dispatcher.unsafeRunAndForget(q.offer(c))
               )
             )
           }(x => Async[F].delay(x.close()))
      s <- Resource.pure(Stream.fromQueueUnterminated(q))
    } yield s

    override def eventStream(implicit
      dispatcher: Dispatcher[F],
      concurrent: Concurrent[F]
    ): Resource[F, Stream[F, StreamEvent[T]]] = for {
      vs <- valueStream
      cs <- connectionStream
    } yield vs
      .map(StreamEvent.ValueChanged[T])
      .merge(cs.map(_.fold(StreamEvent.Connected, StreamEvent.Disconnected)))
  }

  def build[F[_]: Async, T, J](caChannel: CaChannel[J])(implicit
    cv:                                   Convert[T, J]
  ): Channel[F, T] = new ChannelImpl[F, T, J](caChannel)

}
