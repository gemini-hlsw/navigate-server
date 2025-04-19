// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.epics

import cats.effect.IO
import cats.implicits.*
import munit.CatsEffectSuite
import navigate.epics.EpicsSystem.TelltaleChannel
import org.epics.ca.ConnectionState

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class EpicsSystemSpec extends CatsEffectSuite {

  private val epicsServer = ResourceFunFixture(
    TestEpicsServer.init("test:").flatMap(_ => EpicsService.getBuilder.build[IO])
  )

  epicsServer.test("Connect all channels") { srv =>
    assertIOBoolean {
      (for {
        ch0 <- srv.getChannel[Int]("test:intVal")
        ch1 <- srv.getChannel[Double]("test:doubleVal")
        ch2 <- srv.getChannel[String]("test:stringVal")
      } yield (ch0, ch1, ch2))
        .use { case (ch0, ch1, ch2) =>
          for {
            _ <- EpicsSystem[IO](TelltaleChannel("foo", ch0), Set(ch1, ch2)).connectionCheck()
            r <- List[RemoteChannel[IO]](ch0, ch1, ch1).map(_.getConnectionState).parSequence
          } yield r.forall(_.equals(ConnectionState.CONNECTED))
        }
    }
  }

  epicsServer.test("Does not connect channels if it cannot connect telltale channel") { srv =>
    assertIOBoolean {
      (for {
        ch0 <- srv.getChannel[Int]("test:foo")
        ch1 <- srv.getChannel[Double]("test:doubleVal")
        ch2 <- srv.getChannel[String]("test:stringVal")
      } yield (ch0, ch1, ch2))
        .use { case (ch0, ch1, ch2) =>
          for {
            _ <- EpicsSystem[IO](TelltaleChannel("foo", ch0), Set(ch1, ch2)).connectionCheck(
                   FiniteDuration(1, TimeUnit.SECONDS)
                 )
            r <- List[RemoteChannel[IO]](ch0, ch1, ch1).map(_.getConnectionState).parSequence
          } yield !r.exists(_.equals(ConnectionState.CONNECTED))
        }
    }
  }

}
