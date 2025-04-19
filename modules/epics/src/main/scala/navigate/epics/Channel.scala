// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.epics

import cats.Eq
import cats.effect.Async
import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.implicits.*
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import cats.implicits.*
import fs2.Stream
import mouse.all.*
import navigate.epics.Channel.StreamEvent
import navigate.epics.RemoteChannel.RemoteChannelImpl
import org.epics.ca.Channel as CaChannel
import org.epics.ca.Severity
import org.epics.ca.Status

import java.lang.Boolean as JBoolean
import scala.concurrent.duration.FiniteDuration

trait Channel[F[_], T] extends RemoteChannel[F] {
  val get: F[T]
  def get(timeout: FiniteDuration): F[T]
  def put(v:       T): F[Unit]

  /**
   * Stream of channel values.
   * @return
   *   The stream of values, contained inside a Resource
   */
  def valueStream(using dispatcher: Dispatcher[F]): Resource[F, Stream[F, T]]

  /**
   * Stream of connection events. The values are of type <code>Boolean</code>, <code>true</code>
   * means the channels is connected, <code>false</code> means that the channel is disconnected.
   * @return
   *   The stream of events, contained inside a Resource
   */
  def connectionStream(using dispatcher: Dispatcher[F]): Resource[F, Stream[F, Boolean]]

  /**
   * Stream combining the values and connection events streams.
   * @return
   *   The stream, contained inside a Resource
   */
  def eventStream(using
    dispatcher: Dispatcher[F],
    concurrent: Concurrent[F]
  ): Resource[F, Stream[F, StreamEvent[T]]]
}

object Channel {

  sealed trait StreamEvent[+T]

  object StreamEvent {
    case object Connected            extends StreamEvent[Nothing]
    case object Disconnected         extends StreamEvent[Nothing]
    case class ValueChanged[T](v: T) extends StreamEvent[T]

    given [T: Eq]: Eq[StreamEvent[T]] = Eq.instance {
      case (Connected, Connected)             => true
      case (Disconnected, Disconnected)       => true
      case (ValueChanged(a), ValueChanged(b)) => (a: T) === b
      case _                                  => false
    }

  }

  private final class ChannelImpl[F[_]: Async, T, J](override val caChannel: CaChannel[J])(using
    cv: Convert[T, J]
  ) extends RemoteChannelImpl[F]
      with Channel[F, T] {
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
        if (s.getSeverity == Severity.SUCCESS) Async[F].unit
        else Async[F].raiseError(new Throwable(s.getMessage))
      }

    override def valueStream(using dispatcher: Dispatcher[F]): Resource[F, Stream[F, T]] = for {
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

    override def connectionStream(using
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

    override def eventStream(using
      dispatcher: Dispatcher[F],
      concurrent: Concurrent[F]
    ): Resource[F, Stream[F, StreamEvent[T]]] = for {
      vs <- valueStream
      cs <- connectionStream
    } yield vs
      .map(StreamEvent.ValueChanged[T])
      .merge(cs.map(_.fold(StreamEvent.Connected, StreamEvent.Disconnected)))
  }

  def build[F[_]: Async, T, J](caChannel: CaChannel[J])(using
    cv: Convert[T, J]
  ): Channel[F, T] = new ChannelImpl[F, T, J](caChannel)

}
