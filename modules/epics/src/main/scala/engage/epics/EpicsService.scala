// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.effect.Async
import cats.effect.Resource
import cats.syntax.option._
import org.epics.ca.Context
import org.epics.ca.impl.ProtocolConfiguration.PropertyNames.EPICS_CA_ADDR_LIST
import org.epics.ca.impl.ProtocolConfiguration.PropertyNames.EPICS_CA_AUTO_ADDR_LIST
import org.epics.ca.impl.ProtocolConfiguration.PropertyNames.EPICS_CA_CONN_TMO
import org.epics.ca.impl.ProtocolConfiguration.PropertyNames.EPICS_CA_MAX_ARRAY_BYTES
import org.epics.ca.impl.ProtocolConfiguration.PropertyNames.EPICS_CA_REPEATER_PORT
import org.epics.ca.impl.ProtocolConfiguration.PropertyNames.EPICS_CA_SERVER_PORT

import java.net.InetAddress
import java.util.Properties
import scala.concurrent.duration.FiniteDuration

trait EpicsService[F[_]] {
  def getChannel[T](name: String)(implicit tjt: ToJavaType[T]): Resource[F, Channel[F, T]]
}

object EpicsService {

  final class EpicsServiceImpl[F[_]: Async](ctx: Context) extends EpicsService[F] {
    override def getChannel[T](
      name:         String
    )(implicit tjt: ToJavaType[T]): Resource[F, Channel[F, T]] = Resource
      .make(
        Async[F]
          .delay(ctx.createChannel[tjt.javaType](name, tjt.clazz))
      )(c => Async[F].delay(c.close()))
      .map(x => Channel.build[F, T, tjt.javaType](x)(Async[F], tjt.convert))
  }

  final case class Builder(
    addrList:          Option[List[InetAddress]],
    autoAddrList:      Option[Boolean],
    connectionTimeout: Option[FiniteDuration],
    repeaterPort:      Option[Int],
    serverPort:        Option[Int],
    maxArrayBytes:     Option[Int]
  ) {
    def withAddressList(l:        List[InetAddress]): Builder = this.copy(addrList = l.some)
    def withAutoAddrList(enabled: Boolean): Builder           = this.copy(autoAddrList = enabled.some)
    def withConnectionTimeout(timeout: FiniteDuration): Builder =
      this.copy(connectionTimeout = timeout.some)
    def withRepeaterPort(port:   Int): Builder = this.copy(repeaterPort = port.some)
    def withServerPort(port:     Int): Builder = this.copy(serverPort = port.some)
    def withMaxArrayBytes(limit: Int): Builder = this.copy(maxArrayBytes = limit.some)

    def build[F[_]: Async]: Resource[F, EpicsService[F]] = Resource
      .eval {
        Async[F].delay {
          val props: Properties = System.getProperties
          addrList.map(l => props.setProperty(EPICS_CA_ADDR_LIST.toString, l.mkString(" ")))
          autoAddrList.map(e => props.setProperty(EPICS_CA_AUTO_ADDR_LIST.toString, e.toString))
          connectionTimeout.map(x =>
            props.setProperty(EPICS_CA_CONN_TMO.toString, x.toSeconds.toString)
          )
          repeaterPort.map(x => props.setProperty(EPICS_CA_REPEATER_PORT.toString, x.toString))
          serverPort.map(x => props.setProperty(EPICS_CA_SERVER_PORT.toString, x.toString))
          maxArrayBytes.map(x => props.setProperty(EPICS_CA_MAX_ARRAY_BYTES.toString, x.toString))

          props
        }
      }
      .flatMap(getContext(_))
      .map(new EpicsServiceImpl[F](_))
  }

  def getBuilder: Builder = Builder(none, none, none, none, none, none)

  private def getContext[F[_]: Async](properties: Properties): Resource[F, Context] =
    Resource.make {
      Async[F].delay(new Context(properties))
    } { c =>
      Async[F].delay(c.close())
    }

}
