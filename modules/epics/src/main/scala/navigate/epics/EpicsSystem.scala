// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.epics

import cats.Parallel
import cats.effect.Async
import cats.syntax.all.*
import mouse.all.*
import navigate.epics.EpicsSystem.TelltaleChannel
import org.epics.ca.ConnectionState

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

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
case class EpicsSystem[F[_]: {Async, Parallel}](
  telltale:    TelltaleChannel[F],
  channelList: Set[RemoteChannel[F]]
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
  def connectionsCheck(
    connectionTimeout: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
  ): F[(Boolean, List[(String, Boolean)])] = telltaleConnectionCheck(connectionTimeout)
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
                .flatMap(t => ch.getName.map((_, t)))
            )
          )
          .toList
          .parSequence
          .map { a =>
            val r = a.filter(!_._2)
            (r.isEmpty, r)
          },
        (false, List.empty).pure[F]
      )
    )

  def connectionCheck(
    connectionTimeout: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
  ): F[Boolean] = connectionsCheck(connectionTimeout).map(_._1)

}

object EpicsSystem {

  case class TelltaleChannel[F[_]](sysName: String, channel: RemoteChannel[F])

}
