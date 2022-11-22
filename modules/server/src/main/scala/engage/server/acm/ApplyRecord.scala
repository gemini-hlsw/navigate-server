// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.acm

import cats.effect.Resource
import engage.epics._
import engage.server.epicsdata.DirSuffix

case class ApplyRecord[F[_]](
  name: String,
  dir:  Channel[F, CadDirective],
  oval: Channel[F, Int],
  mess: Channel[F, String]
)
object ApplyRecord {
  private val ValSuffix = ".VAL"
  private val MsgSuffix = ".MESS"

  def build[F[_]](srv: EpicsService[F], applyName: String): Resource[F, ApplyRecord[F]] = for {
    v   <- srv.getChannel[Int](applyName + ValSuffix)
    dir <- srv.getChannel[CadDirective](applyName + DirSuffix)
    ms  <- srv.getChannel[String](applyName + MsgSuffix)
  } yield ApplyRecord(applyName, dir, v, ms)

}
