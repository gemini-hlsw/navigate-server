// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.epics

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import munit.CatsEffectSuite

import java.util.concurrent.TimeUnit
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS

class EpicsServiceSpec extends CatsEffectSuite {

  private val epicsServer = ResourceFunFixture(
    TestEpicsServer.init("test:").flatMap(_ => EpicsService.getBuilder.build[IO])
  )

  given Class[TestEnumerated] = classOf[TestEnumerated]

  epicsServer.test("Timeout trying to connect to nonexistent channel") { srv =>
    interceptIO[TimeoutException](
      srv
        .getChannel[Int]("test:foo")
        .use(ch =>
          for {
            _ <- ch.connect(FiniteDuration(1, SECONDS))
            v <- ch.get
          } yield v
        )
    )
  }

  epicsServer.test("Read Int channel") { srv =>
    assertIO(
      srv
        .getChannel[Int]("test:intVal")
        .use(ch =>
          for {
            _ <- ch.connect
            v <- ch.get
          } yield v
        ),
      0
    )
  }

  epicsServer.test("Read Double channel") { srv =>
    assertIO(
      srv
        .getChannel[Double]("test:doubleVal")
        .use(ch =>
          for {
            _ <- ch.connect
            v <- ch.get
          } yield v
        ),
      0.0
    )
  }

  epicsServer.test("Read Float channel") { srv =>
    assertIO(
      srv
        .getChannel[Float]("test:floatVal")
        .use(ch =>
          for {
            _ <- ch.connect
            v <- ch.get
          } yield v
        ),
      0.0f
    )
  }

  epicsServer.test("Read String channel") { srv =>
    assertIO(
      srv
        .getChannel[String]("test:stringVal")
        .use(ch =>
          for {
            _ <- ch.connect
            v <- ch.get
          } yield v
        ),
      "dummy"
    )
  }

  epicsServer.test("Read Enum channel") { srv =>
    assertIO(
      srv
        .getChannel[TestEnumerated]("test:enumVal")
        .use(ch =>
          for {
            _ <- ch.connect
            v <- ch.get
          } yield v
        ),
      TestEnumerated.VAL0
    )
  }

  epicsServer.test("Read stream of values") { srv =>
    assertIO(
      (
        for {
          dsp <- Dispatcher.sequential[IO]
          ch  <- srv.getChannel[Int]("test:heartbeat")
          _   <- Resource.eval(ch.connect)
          s   <- ch.valueStream(using dsp)
        } yield s
      ).use(_.drop(2).take(5).compile.toList)
        .map(l => l.map(_ - l.head))
        .timeout(FiniteDuration(5, TimeUnit.SECONDS)),
      List.range(0, 5)
    )
  }

  epicsServer.test("Write to Int channel") { srv =>
    val t: Int = 0xdeadbeef
    assertIO(
      srv
        .getChannel[Int]("test:intVal")
        .use(ch =>
          for {
            _ <- ch.connect
            _ <- ch.put(t)
            v <- ch.get
          } yield v
        ),
      t
    )
  }

  epicsServer.test("Write to Double channel") { srv =>
    val t: Double = 3.141592653
    assertIO(
      srv
        .getChannel[Double]("test:doubleVal")
        .use(ch =>
          for {
            _ <- ch.connect
            _ <- ch.put(t)
            v <- ch.get
          } yield v
        ),
      t
    )
  }

  epicsServer.test("Write to Float channel") { srv =>
    val t: Float = 3.141592653f
    assertIO(
      srv
        .getChannel[Float]("test:floatVal")
        .use(ch =>
          for {
            _ <- ch.connect
            _ <- ch.put(t)
            v <- ch.get
          } yield v
        ),
      t
    )
  }

  epicsServer.test("Write to String channel") { srv =>
    val t: String = "deadbeef"
    assertIO(
      srv
        .getChannel[String]("test:stringVal")
        .use(ch =>
          for {
            _ <- ch.connect
            _ <- ch.put(t)
            v <- ch.get
          } yield v
        ),
      t
    )
  }

  epicsServer.test("Write to Enum channel") { srv =>
    val t: TestEnumerated = TestEnumerated.VAL2
    assertIO(
      srv
        .getChannel[TestEnumerated]("test:enumVal")
        .use(ch =>
          for {
            _ <- ch.connect
            _ <- ch.put(t)
            v <- ch.get
          } yield v
        ),
      t
    )
  }

  epicsServer.test("Error trying to read from unconnected channel") { srv =>
    interceptIO[IllegalStateException](
      srv
        .getChannel[Int]("test:intVal")
        .use(ch =>
          for {
            v <- ch.get
          } yield v
        )
    )
  }

  epicsServer.test("Error trying to write to unconnected channel") { srv =>
    interceptIO[IllegalStateException](
      srv
        .getChannel[Int]("test:intVal")
        .use(ch =>
          for {
            v <- ch.put(1)
          } yield v
        )
    )
  }

  epicsServer.test("Error trying to get stream from unconnected channel") { srv =>
    interceptIO[IllegalStateException](
      (for {
        dsp <- Dispatcher.sequential[IO]
        ch  <- srv.getChannel[Int]("test:intVal")
        v   <- ch.valueStream(using dsp)
      } yield v).use_
    )
  }

}
