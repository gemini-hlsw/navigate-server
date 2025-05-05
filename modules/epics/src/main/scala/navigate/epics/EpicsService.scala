// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.epics

import cats.effect.Async
import cats.effect.Resource
import cats.syntax.option.*
import eu.timepit.refined.types.string.NonEmptyString
import org.epics.ca.Context
import org.epics.ca.impl.LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE
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
  def getChannel[T](name: String)(using tjt: ToJavaType[T]): Resource[F, Channel[F, T]]
  def getChannel[T](top: NonEmptyString, name: String)(using
    tjt: ToJavaType[T]
  ): Resource[F, Channel[F, T]]
}

object EpicsService {

  final class EpicsServiceImpl[F[_]: Async](ctx: Context) extends EpicsService[F] {
    def getChannel[T](
      name: String
    )(using tjt: ToJavaType[T]): Resource[F, Channel[F, T]] = Resource
      .make(
        Async[F]
          .delay(ctx.createChannel[tjt.javaType](name, tjt.clazz))
      )(c => Async[F].delay(c.close()))
      .map(x => Channel.build(x)(using Async[F], tjt.convert))

    def getChannel[T](top: NonEmptyString, name: String)(using
      tjt: ToJavaType[T]
    ): Resource[F, Channel[F, T]] =
      getChannel(s"${top.value}$name")

  }

  case class Builder(
    addrList:          Option[List[InetAddress]],
    autoAddrList:      Option[Boolean],
    connectionTimeout: Option[FiniteDuration],
    enableRepeater:    Option[Boolean],
    repeaterPort:      Option[Int],
    serverPort:        Option[Int],
    maxArrayBytes:     Option[Int]
  ) {
    def withAddressList(l:        List[InetAddress]): Builder   = this.copy(addrList = l.some)
    def withAutoAddrList(enabled: Boolean): Builder             = this.copy(autoAddrList = enabled.some)
    def withConnectionTimeout(timeout: FiniteDuration): Builder =
      this.copy(connectionTimeout = timeout.some)
    def withEnabledRepeater(enabled: Boolean): Builder          =
      this.copy(enableRepeater = enabled.some)
    def withRepeaterPort(port:    Int): Builder                 = this.copy(repeaterPort = port.some)
    def withServerPort(port:      Int): Builder                 = this.copy(serverPort = port.some)
    def withMaxArrayBytes(limit:  Int): Builder                 = this.copy(maxArrayBytes = limit.some)

    def build[F[_]: Async]: Resource[F, EpicsService[F]] = Resource
      .eval {
        Async[F].delay {
          val props: Properties = System.getProperties
          addrList.map(l =>
            props.setProperty(EPICS_CA_ADDR_LIST.toString, l.map(_.getHostAddress).mkString(" "))
          )
          autoAddrList.map(e => props.setProperty(EPICS_CA_AUTO_ADDR_LIST.toString, e.toString))
          connectionTimeout.map(x =>
            props.setProperty(EPICS_CA_CONN_TMO.toString, x.toSeconds.toString)
          )
          enableRepeater.map(x => props.setProperty(CA_REPEATER_DISABLE.toString, (!x).toString))
          repeaterPort.map(x => props.setProperty(EPICS_CA_REPEATER_PORT.toString, x.toString))
          serverPort.map(x => props.setProperty(EPICS_CA_SERVER_PORT.toString, x.toString))
          maxArrayBytes.map(x => props.setProperty(EPICS_CA_MAX_ARRAY_BYTES.toString, x.toString))

          props
        }
      }
      .flatMap(getContext(_))
      .map(new EpicsServiceImpl[F](_))
  }

  def getBuilder: Builder = Builder(none, none, none, none, none, none, none)

  private def getContext[F[_]: Async](properties: Properties): Resource[F, Context] =
    Resource.make {
      Async[F].delay(new Context(properties))
    } { c =>
      Async[F].delay(c.close())
    }

}
