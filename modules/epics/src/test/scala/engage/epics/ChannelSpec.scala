// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.SyncIO
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.implicits._
import gov.aps.jca.cas.ServerContext
import munit.CatsEffectSuite

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import Channel.StreamEvent._

class ChannelSpec extends CatsEffectSuite {

  private val epicsServer: SyncIO[FunFixture[(ServerContext, EpicsService[IO])]] = ResourceFixture(
    TestEpicsServer
      .init("test:")
      .flatMap(x =>
        EpicsService.getBuilder
          .withConnectionTimeout(FiniteDuration(1, TimeUnit.SECONDS))
          .build[IO]
          .map((x, _))
      )
  )

  epicsServer.test("Read stream of channel events") { case (serverCtx, srv) =>
    assertIOBoolean(
      (
        for {
          dsp <- Dispatcher[IO]
          ch  <- srv.getChannel[Int]("test:heartbeat")
          _   <- Resource.eval(ch.connect)
          s   <- ch.eventStream(dsp, Concurrent[IO])
        } yield s
      ).use {
        _.zipWithIndex
          .evalMap { case (x, i) =>
            (
              if (i == 10) IO.delay(serverCtx.destroy())
              else IO.unit
            ).as(x)
          }
          .takeThrough {
            case Channel.StreamEvent.Disconnected => false
            case _                                => true
          }
          .compile
          .toList
      }.map(checkStream)
    )

  }

  private def checkStream(l: List[Channel.StreamEvent[Int]]): Boolean = {

    val ns: List[Int] = l.collect { case Channel.StreamEvent.ValueChanged(v) => v }

    List.range(0, ns.length) === ns.map(_ - ns.head) && l.last === Channel.StreamEvent.Disconnected
  }

}
