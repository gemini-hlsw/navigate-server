// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.epics

import cats.effect.IO
import cats.effect.std.Dispatcher
import munit.CatsEffectSuite
import navigate.epics.Channel.StreamEvent
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.VerifiedEpics.*
import org.epics.ca.ConnectionState

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class VerifiedEpicsSpec extends CatsEffectSuite {

  private val epicsService = ResourceFunFixture(EpicsService.getBuilder.build[IO])

  /* This test works by creating the client channels before starting the test EPICS server. That way we are sure the
   * channels are disconnected by the time we try to use them, proving that verifiedRun works.
   */
  epicsService.test("Makes sure channels are connected before reading and writing") { service =>
    val testVal: Int = 1
    (for {
      tt  <- service.getChannel[Int]("test:stringVal").map(c => TelltaleChannel("foo", c))
      ch1 <- service.getChannel[Double]("test:doubleVal")
      ch2 <- service.getChannel[Int]("test:intVal")
    } yield (tt, ch1, ch2))
      .use { case (tt, ch1, ch2) =>
        val q = for {
          _  <- VerifiedEpics.writeChannel(tt, ch1)(IO.pure(testVal.toDouble))
          fa <- VerifiedEpics.readChannel(tt, ch1).map(_.map(_ + 1))
          _  <- VerifiedEpics.writeChannel(tt, ch2)(fa.map(_.toInt))
          fr <- VerifiedEpics.readChannel[IO, Int](tt, ch2)
        } yield fr

        for {
          tts1  <- tt.channel.getConnectionState
          ch1s1 <- ch1.getConnectionState
          r     <- TestEpicsServer.init("test:").use { _ =>
                     q.verifiedRun(FiniteDuration(1, TimeUnit.SECONDS))
                   }
        } yield {
          assertEquals(tts1, ConnectionState.NEVER_CONNECTED)
          assertEquals(ch1s1, ConnectionState.NEVER_CONNECTED)
          assertEquals(r, testVal + 1)
        }
      }
  }

  epicsService.test("Makes sure channels are connected before reading a stream") { service =>
    val valueCount = 5
    (for {
      tt  <- service.getChannel[Int]("test:stringVal").map(c => TelltaleChannel("foo", c))
      ch1 <- service.getChannel[Int]("test:heartbeat")
      dsp <- Dispatcher.sequential[IO]
    } yield (tt, ch1, dsp))
      .use { case (tt, ch1, dsp) =>
        given Dispatcher[IO] = dsp
        val q                = VerifiedEpics
          .eventStream(tt, ch1)
          .map(
            _.use(
              _.collect { case StreamEvent.ValueChanged(x) => x }
                .take(valueCount.toLong)
                .compile
                .toList
            )
          )

        for {
          tts1  <- tt.channel.getConnectionState
          ch1s1 <- ch1.getConnectionState
          r     <- TestEpicsServer.init("test:").use { _ =>
                     q.verifiedRun(FiniteDuration(1, TimeUnit.SECONDS))
                   }
        } yield {
          assertEquals(tts1, ConnectionState.NEVER_CONNECTED)
          assertEquals(ch1s1, ConnectionState.NEVER_CONNECTED)
          assertEquals(r.length, valueCount)
          assertEquals(r.map(_ - r.head), List.range(0, valueCount))
        }
      }
  }

  epicsService.test(
    "Raises an Exception if it cannot connect channels when running a computation."
  ) { service =>
    interceptIO[Throwable] {
      val testVal: Int = 1
      (for {
        tt  <- service.getChannel[Int]("test:nonexistent").map(c => TelltaleChannel("foo", c))
        ch1 <- service.getChannel[Double]("test:doubleVal")
        ch2 <- service.getChannel[Int]("test:intVal")
      } yield (tt, ch1, ch2))
        .use { case (tt, ch1, ch2) =>
          val q = for {
            _  <- VerifiedEpics.writeChannel(tt, ch1)(IO.pure(testVal.toDouble))
            fa <- VerifiedEpics.readChannel(tt, ch1).map(_.map(_ + 1))
            _  <- VerifiedEpics.writeChannel(tt, ch2)(fa.map(_.toInt))
            fr <- VerifiedEpics.readChannel[IO, Int](tt, ch2)
          } yield fr

          for {
            tts1  <- tt.channel.getConnectionState
            ch1s1 <- ch1.getConnectionState
            r     <- TestEpicsServer.init("test:").use { _ =>
                       q.verifiedRun(FiniteDuration(1, TimeUnit.SECONDS))
                     }
          } yield {
            assertEquals(tts1, ConnectionState.NEVER_CONNECTED)
            assertEquals(ch1s1, ConnectionState.NEVER_CONNECTED)
            assertEquals(r, testVal + 1)
          }
        }
    }
  }

}
