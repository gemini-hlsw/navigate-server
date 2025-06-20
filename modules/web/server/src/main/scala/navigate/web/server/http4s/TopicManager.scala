// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.Applicative
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.effect.syntax.all.*
import cats.syntax.all.*
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import fs2.Stream
import fs2.concurrent.Topic
import navigate.model.AcquisitionAdjustment
import navigate.model.FocalPlaneOffset
import navigate.model.NavigateEvent
import navigate.model.PointingCorrections
import navigate.server.NavigateEngine
import navigate.server.NavigateFailure
import navigate.server.tcs.GuideState
import navigate.server.tcs.GuidersQualityValues
import navigate.server.tcs.TargetOffsets
import navigate.server.tcs.TelescopeState
import navigate.web.server.logging.SubscriptionAppender
import org.typelevel.log4cats.Logger

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

class TopicManager[F[_]] private (
  val navigateEvents:        Topic[F, NavigateEvent],
  val loggingEvents:         Topic[F, ILoggingEvent],
  val guideState:            Topic[F, GuideState],
  val guidersQuality:        Topic[F, GuidersQualityValues],
  val telescopeState:        Topic[F, TelescopeState],
  val acquisitionAdjustment: Topic[F, AcquisitionAdjustment],
  val targetAdjustment:      Topic[F, TargetOffsets],
  val originAdjustment:      Topic[F, FocalPlaneOffset],
  val pointingAdjustment:    Topic[F, PointingCorrections],
  val logBuffer:             Ref[F, Seq[ILoggingEvent]]
) {

  private def genericPoll[A](
    fetchData: => F[A],
    topic:     Topic[F, A]
  )(using Temporal[F]): Stream[F, Unit] =
    Stream
      .fixedRate[F](FiniteDuration(1, TimeUnit.SECONDS))
      .evalMap(_ => fetchData)
      .evalMapAccumulate(none[A]) { (acc, data) =>
        (if (acc.contains(data)) Applicative[F].unit else topic.publish1(data).void)
          .as(data.some, ())
      }
      .void

  private def guideStatePoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, GuideState]
  )(using Temporal[F]): Stream[F, Unit] =
    genericPoll(eng.getGuideState, topic)

  private def guiderQualityPoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, GuidersQualityValues]
  )(using Temporal[F]): Stream[F, Unit] =
    genericPoll(eng.getGuidersQuality, topic)

  private def telescopeStatePoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, TelescopeState]
  )(using Temporal[F]): Stream[F, Unit] =
    genericPoll(eng.getTelescopeState, topic)

  // Logger of error of last resort.
  private def logError[F[_]: Logger]: PartialFunction[Throwable, F[Unit]] = {
    case e: NavigateFailure =>
      Logger[F].error(e)(s"Navigate global error handler ${NavigateFailure.explain(e)}")
    case e: Exception       => Logger[F].error(e)("Navigate global error handler")
  }

  /**
   * Start all topics and monitoring in a single call
   */
  def startAll(
    engine: NavigateEngine[F]
  )(using Temporal[F], Logger[F]): F[Fiber[F, Throwable, Unit]] =
    for {
      // Start monitoring subscribers
      _ <- navigateEvents.subscribers
             .evalMap(l => Logger[F].debug(s"Subscribers amount: $l").whenA(l > 1))
             .compile
             .drain
             .start

      // Start all polling streams
      _ <- guideStatePoll(engine, guideState).compile.drain.start
      _ <- guiderQualityPoll(engine, guidersQuality).compile.drain.start
      _ <- telescopeStatePoll(engine, telescopeState).compile.drain.start

      // Start engine event stream
      fiber <-
        engine.eventStream.through(navigateEvents.publish).compile.drain.onError(logError).start
    } yield fiber
}

object TopicManager {

  /**
   * Buffer log messages to be able to send old messages to new clients
   */
  private def bufferLogMessages[F[_]: Concurrent](
    log: Topic[F, ILoggingEvent]
  ): Resource[F, Ref[F, Seq[ILoggingEvent]]] = {
    val maxQueueSize = 30
    for {
      buffer <- Ref.empty[F, Seq[ILoggingEvent]].toResource
      _      <-
        log
          .subscribe(1024)
          .evalMap(event => buffer.update(events => events.takeRight(maxQueueSize - 1) :+ event))
          .compile
          .drain
          .background
    } yield buffer
  }

  // We need to manually update the configuration of the logging subsystem
  // to support capturing log messages and forward them to the clients
  private def logToClients[F[_]: Sync](
    out:        Topic[F, ILoggingEvent],
    dispatcher: Dispatcher[F]
  ): F[Appender[ILoggingEvent]] = Sync[F].blocking {
    import ch.qos.logback.classic.{AsyncAppender, Logger, LoggerContext}
    import org.slf4j.LoggerFactory

    val asyncAppender = new AsyncAppender
    val appender      = new SubscriptionAppender[F](out)(using dispatcher)
    Option(LoggerFactory.getILoggerFactory)
      .collect { case lc: LoggerContext =>
        lc
      }
      .foreach { ctx =>
        asyncAppender.setContext(ctx)
        appender.setContext(ctx)
        asyncAppender.addAppender(appender)
      }

    Option(LoggerFactory.getLogger("navigate"))
      .collect { case l: Logger =>
        l
      }
      .foreach { l =>
        l.addAppender(asyncAppender)
        asyncAppender.start()
        appender.start()
      }
    asyncAppender
  }

  /**
   * Create a new TopicManager with all topics initialized
   */
  def create[F[_]: Async](dispatcher: Dispatcher[F]): Resource[F, TopicManager[F]] =
    for {
      navigateEvents        <- Resource.eval(Topic[F, NavigateEvent])
      loggingEvents         <- Resource.eval(Topic[F, ILoggingEvent])
      guideState            <- Resource.eval(Topic[F, GuideState])
      guidersQuality        <- Resource.eval(Topic[F, GuidersQualityValues])
      telescopeState        <- Resource.eval(Topic[F, TelescopeState])
      acquisitionAdjustment <- Resource.eval(Topic[F, AcquisitionAdjustment])
      targetAdjustment      <- Resource.eval(Topic[F, TargetOffsets])
      originAdjustment      <- Resource.eval(Topic[F, FocalPlaneOffset])
      pointingAdjustment    <- Resource.eval(Topic[F, PointingCorrections])

      // Setup log buffer
      logBuffer <- bufferLogMessages(loggingEvents)
      // Setup logging to clients
      _         <- Resource.eval(logToClients(loggingEvents, dispatcher))
    } yield new TopicManager(
      navigateEvents,
      loggingEvents,
      guideState,
      guidersQuality,
      telescopeState,
      acquisitionAdjustment,
      targetAdjustment,
      originAdjustment,
      pointingAdjustment,
      logBuffer
    )
}
