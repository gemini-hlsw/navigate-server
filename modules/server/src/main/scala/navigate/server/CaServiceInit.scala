// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.effect.Async
import cats.effect.Resource
import cats.syntax.all.*
import navigate.epics.EpicsService
import navigate.model.config.NavigateEngineConfiguration
import org.typelevel.log4cats.Logger

import java.net.InetAddress

object CaServiceInit {
  // Ensure there is a valid way to init CaService either from
  // the configuration file or from the environment
  def caInit[F[_]: Async](
    conf: NavigateEngineConfiguration
  )(using L: Logger[F]): Resource[F, EpicsService[F]] = {
    val addressList = conf.epicsCaAddrList
      .map(_.pure[F])
      .getOrElse {
        Async[F].delay(Option(System.getenv("EPICS_CA_ADDR_LIST"))).flatMap {
          case Some(a) => a.pure[F]
          case _       =>
            Async[F].raiseError[String](new RuntimeException("Cannot initialize EPICS subsystem"))
        }
      }
      .map(_.split(Array(',', ' ')).toList.map(InetAddress.getByName))

    for {
      addrl <- Resource.eval(
                 L.info("Init EPICS but all subsystems in simulation")
                   .unlessA(conf.systemControl.connectEpics) *>
                   addressList
               )
      srv   <-
        EpicsService.getBuilder
          .withConnectionTimeout(conf.ioTimeout)
          .withAddressList(addrl)
          .build[F]
    } yield srv
  }
}
