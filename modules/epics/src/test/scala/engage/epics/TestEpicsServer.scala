// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.effect.{ Async, IO, Resource }
import com.cosylab.epics.caj.cas.util.{ DefaultServerImpl, MemoryProcessVariable }
import gov.aps.jca.dbr.DBRType
import gov.aps.jca.JCALibrary
import gov.aps.jca.cas.ServerContext
import lucuma.core.util.Enumerated
import fs2.Stream

import java.lang.{ Integer => JInteger }
import java.lang.{ Double => JDouble }
import java.lang.{ Float => JFloat }
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object TestEpicsServer {

  trait ToDBRType[T] { val dbrType: DBRType }

  object ToDBRType {
    def apply[T](t: DBRType): ToDBRType[T] = new ToDBRType[T] { override val dbrType: DBRType = t }
  }

  implicit val intDBRType: ToDBRType[JInteger]         = ToDBRType(DBRType.INT)
  implicit val doubleDBRType: ToDBRType[JDouble]       = ToDBRType(DBRType.DOUBLE)
  implicit val floatDBRType: ToDBRType[JFloat]         = ToDBRType(DBRType.FLOAT)
  implicit val stringDBRType: ToDBRType[String]        = ToDBRType(DBRType.STRING)
  implicit def enumDBRType[T <: Enum[T]]: ToDBRType[T] =
    ToDBRType(DBRType.ENUM)

  val jcaLibrary: JCALibrary = JCALibrary.getInstance()

  def createPV[F[_]: Async, T](server: DefaultServerImpl, name: String, init: Object)(implicit
    t:                                 ToDBRType[T]
  ): Resource[F, MemoryProcessVariable] =
    Resource.make {
      Async[F].delay {
        server.createMemoryProcessVariable(name, t.dbrType, init)
      }
    } { x =>
      Async[F].delay {
        server.unregisterProcessVaribale(name)
        x.destroy()
      }
    }

  def createPVEnum[F[_]: Async, T: Enumerated](
    server: DefaultServerImpl,
    name:   String,
    init:   T
  ): Resource[F, MemoryProcessVariable] =
    Resource.make {
      Async[F].delay {
        val index: Short = Enumerated[T].all.indexOf(init).toShort
        val memPV        = TestEpicsUtil.createEnumMemoryProcessVariable(name, Array(index))
        memPV.setEnumLabels(Enumerated[T].all.map(_.toString()).toArray)
        server.registerProcessVaribale(memPV)
        memPV
      }
    } { x =>
      Async[F].delay {
        server.unregisterProcessVaribale(name)
        x.destroy()
      }
    }

  def init(top: String): Resource[IO, ServerContext] =
    for {
      server <- Resource.eval(IO.delay(new DefaultServerImpl()))
      ctx    <- Resource.make {
                  IO.delay(
                    jcaLibrary.createServerContext(JCALibrary.CHANNEL_ACCESS_SERVER_JAVA, server)
                  )
                } { x =>
                  IO.delay(
                    x.dispose()
                  )
                }
      _      <- createPV[IO, JInteger](server, top ++ "intVal", Array(0))
      _      <- createPV[IO, JDouble](server, top ++ "doubleVal", Array(0.0))
      _      <- createPV[IO, JFloat](server, top ++ "floatVal", Array(0.0f))
      _      <- createPV[IO, String](server, top ++ "stringVal", Array("dummy"))
      _      <- createPVEnum[IO, TestEnumerated](server, top ++ "enumVal", TestEnumerated.VAL0)
      h      <- createPV[IO, JInteger](server, top ++ "heartbeat", Array(0))
      _      <- Resource.make {
                  IO.delay(ctx.run(0)).start.void
                }(_ => IO.delay(ctx.shutdown()))
      _      <- Stream
                  .awakeEvery[IO](FiniteDuration(200, TimeUnit.MILLISECONDS))
                  .evalMapAccumulate(1) { (s: Int, _: FiniteDuration) =>
                    IO.delay {
                      h.write(TestEpicsUtil.dbrIntValue(JInteger.valueOf(s)), null)
                      (s + 1, ())
                    }
                  }
                  .compile
                  .last
                  .background
    } yield ctx

}