// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.Parallel
import cats.effect.Async
import cats.syntax.all._
import engage.epics.EpicsSystem.TelltaleChannel
import mouse.all._
import org.epics.ca.ConnectionState

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import RemoteChannel._

/**
 * Class EpicsSystem groups EPICS channels from the same IOC. On eof the channels is designed as a
 * representative of the state of the IOC. Connections and checks are optimized using this channel.
 * If this channel does not connect, the application will not try to connect to the other channels
 * in the list.
 * @param telltale
 *   the channel designed to indicate the state of the IOC
 * @param channelList
 *   the rest of the channels from the IOC
 * @tparam F
 *   Effect that encapsulate the reading and writing of channels.
 */
case class EpicsSystem[F[_]: Async: Parallel](
  telltale:    TelltaleChannel,
  channelList: Set[RemoteChannel]
) {
  def isConnected: F[Boolean] =
    telltale.channel.getConnectionState.map(_.equals(ConnectionState.CONNECTED))

  def isAllConnected: F[Boolean] = isConnected.flatMap(
    _.fold(
      channelList
        .map(_.getConnectionState)
        .toList
        .parSequence
        .map(_.forall(_.equals(ConnectionState.CONNECTED))),
      false.pure[F]
    )
  )

  /**
   * Checks that the telltale channel is connected, and attempts to connect it once if not.
   * @param connectionTimeout
   *   Timeout used when attempting to connect to the channel.
   * @return
   *   <code>true</code> if the channel is connected.
   */
  def telltaleConnectionCheck(
    connectionTimeout: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
  ): F[Boolean] = isConnected.flatMap(
    _.fold(
      true.pure[F],
      telltale.channel.connect(connectionTimeout).attempt.map(_.isRight)
    )
  )

  /**
   * Checks the connection state of all the channels. If a channel is not connected, it attempts to
   * connect to that channel once. If the telltale channel is not connected it assumes the IOC is
   * down, and does not try to connect to any other channel.
   * @param connectionTimeout
   *   Timeout used when attempting to connect to the channels.
   * @return
   *   <code>true</code> if all the channels are connected.
   */
  def connectionCheck(
    connectionTimeout: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
  ): F[Boolean] = telltaleConnectionCheck(connectionTimeout)
    .flatMap(
      _.fold(
        channelList
          .map(ch =>
            ch.getConnectionState.flatMap(x =>
              x.equals(ConnectionState.CONNECTED)
                .fold(
                  true.pure[F],
                  ch.connect(connectionTimeout).attempt.map(_.isRight)
                )
            )
          )
          .toList
          .parSequence
          .map(_.forall(x => x)),
        false.pure[F]
      )
    )
}

object EpicsSystem {

  case class TelltaleChannel(sysName: String, channel: RemoteChannel)

}
