// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.Parallel
import cats.effect.Resource
import cats.effect.Temporal
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import eu.timepit.refined.types.string.NonEmptyString
import mouse.boolean.*
import navigate.epics.*
import navigate.epics.VerifiedEpics.*
import navigate.server.tcs.AgsEpicsSystem.AgsStatus

import FollowStatus.*
import ParkStatus.*

trait AgsEpicsSystem[F[_]] {
  val status: AgsStatus[F]
}

object AgsEpicsSystem {

  trait AgsStatus[F[_]] {
    def inPosition: VerifiedEpics[F, F, Boolean]
    def sfParked: VerifiedEpics[F, F, ParkStatus]
    def aoParked: VerifiedEpics[F, F, ParkStatus]
    def p1Parked: VerifiedEpics[F, F, ParkStatus]
    def p1Follow: VerifiedEpics[F, F, FollowStatus]
    def p2Parked: VerifiedEpics[F, F, ParkStatus]
    def p2Follow: VerifiedEpics[F, F, FollowStatus]
    def oiParked: VerifiedEpics[F, F, ParkStatus]
    def oiFollow: VerifiedEpics[F, F, FollowStatus]
    def flamingos2Port: VerifiedEpics[F, F, Int]
    def ghostPort: VerifiedEpics[F, F, Int]
    def gmosPort: VerifiedEpics[F, F, Int]
    def gnirsPort: VerifiedEpics[F, F, Int]
    def gpiPort: VerifiedEpics[F, F, Int]
    def gsaoiPort: VerifiedEpics[F, F, Int]
    def nifsPort: VerifiedEpics[F, F, Int]
    def niriPort: VerifiedEpics[F, F, Int]
  }

  private[tcs] def buildSystem[F[_]: Applicative](
    channels: AgsChannels[F]
  ): AgsEpicsSystem[F] =
    new AgsEpicsSystem[F] {
      override val status: AgsStatus[F] = new AgsStatus[F] {

        override def inPosition: VerifiedEpics[F, F, Boolean] =
          VerifiedEpics.readChannel(channels.telltale, channels.inPosition).map(_.map(a => a =!= 0))

        override def sfParked: VerifiedEpics[F, F, ParkStatus] = VerifiedEpics
          .readChannel(channels.telltale, channels.sfParked)
          .map(_.map(a => (a =!= 0).fold(Parked, NotParked)))

        override def aoParked: VerifiedEpics[F, F, ParkStatus] = VerifiedEpics
          .readChannel(channels.telltale, channels.aoParked)
          .map(_.map(a => (a =!= 0).fold(Parked, NotParked)))

        override def p1Parked: VerifiedEpics[F, F, ParkStatus] = VerifiedEpics
          .readChannel(channels.telltale, channels.p1Parked)
          .map(_.map(a => (a =!= 0).fold(Parked, NotParked)))

        override def p1Follow: VerifiedEpics[F, F, FollowStatus] =
          VerifiedEpics.readChannel(channels.telltale, channels.p1Follow).map {
            _.map(decodeFollow)
          }

        override def p2Parked: VerifiedEpics[F, F, ParkStatus] = VerifiedEpics
          .readChannel(channels.telltale, channels.p2Parked)
          .map(_.map(a => (a =!= 0).fold(Parked, NotParked)))

        override def p2Follow: VerifiedEpics[F, F, FollowStatus] =
          VerifiedEpics.readChannel(channels.telltale, channels.p2Follow).map {
            _.map(decodeFollow)
          }

        override def oiParked: VerifiedEpics[F, F, ParkStatus] = VerifiedEpics
          .readChannel(channels.telltale, channels.oiParked)
          .map(_.map(a => (a =!= 0).fold(Parked, NotParked)))

        override def oiFollow: VerifiedEpics[F, F, FollowStatus] =
          VerifiedEpics.readChannel(channels.telltale, channels.oiFollow).map {
            _.map(decodeFollow)
          }

        private def decodeFollow(str: String): FollowStatus = str.toUpperCase.trim match {
          case "ON" => Following
          case _    => NotFollowing
        }

        override def flamingos2Port: VerifiedEpics[F, F, Int] =
          VerifiedEpics.readChannel(channels.telltale, channels.instrumentPorts.f2)

        override def ghostPort: VerifiedEpics[F, F, Int] =
          VerifiedEpics.readChannel(channels.telltale, channels.instrumentPorts.ghost)

        override def gmosPort: VerifiedEpics[F, F, Int] =
          VerifiedEpics.readChannel(channels.telltale, channels.instrumentPorts.gmos)

        override def gnirsPort: VerifiedEpics[F, F, Int] =
          VerifiedEpics.readChannel(channels.telltale, channels.instrumentPorts.gnirs)

        override def gpiPort: VerifiedEpics[F, F, Int] =
          VerifiedEpics.readChannel(channels.telltale, channels.instrumentPorts.gpi)

        override def gsaoiPort: VerifiedEpics[F, F, Int] =
          VerifiedEpics.readChannel(channels.telltale, channels.instrumentPorts.gsaoi)

        override def nifsPort: VerifiedEpics[F, F, Int] =
          VerifiedEpics.readChannel(channels.telltale, channels.instrumentPorts.nifs)

        override def niriPort: VerifiedEpics[F, F, Int] =
          VerifiedEpics.readChannel(channels.telltale, channels.instrumentPorts.niri)
      }
    }

  def build[F[_]: {Dispatcher, Temporal, Parallel}](
    service: EpicsService[F],
    top:     NonEmptyString
  ): Resource[F, AgsEpicsSystem[F]] = AgsChannels.build[F](service, top).map(buildSystem)
}
