// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics
import cats.effect.Async
import cats.effect.implicits._
import cats.implicits._
import cats.kernel.Eq
import org.epics.ca.AccessRights
import org.epics.ca.ConnectionState
import org.epics.ca.{Channel => CaChannel}

import scala.concurrent.duration.FiniteDuration

trait RemoteChannel[F[_]] {
  def connect: F[Unit]
  def connect(timeout: FiniteDuration): F[Unit]
  def disconnect: F[Unit]
  def getName: F[String]
  def getConnectionState: F[ConnectionState]
  def getAccessRights: F[AccessRights]
}

object RemoteChannel {

  abstract class RemoteChannelImpl[F[_]: Async] extends RemoteChannel[F] {
    val caChannel: CaChannel[_]

    override def connect: F[Unit] =
      Async[F].fromCompletableFuture(Async[F].delay(caChannel.connectAsync())).void
    override def connect(timeout: FiniteDuration): F[Unit] = connect.timeout(timeout)
    override def disconnect: F[Unit]                    = Async[F].delay(caChannel.close())
    override def getName: F[String]                     = Async[F].delay(caChannel.getName)
    override def getConnectionState: F[ConnectionState] =
      Async[F].delay(caChannel.getConnectionState)
    override def getAccessRights: F[AccessRights]       = Async[F].delay(caChannel.getAccessRights)
  }

  implicit def remoteChannelEq[F[_]]: Eq[RemoteChannel[F]] = Eq.instance {
    case (a: RemoteChannelImpl[F], b: RemoteChannelImpl[F]) => a.caChannel == b.caChannel
    case (a, b)                                             => a.equals(b)
  }

}
