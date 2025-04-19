// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.logging

import cats.effect.std.Dispatcher
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import fs2.concurrent.Topic

class SubscriptionAppender[F[_]](logTopic: Topic[F, ILoggingEvent])(using dispatcher: Dispatcher[F])
    extends UnsynchronizedAppenderBase[ILoggingEvent] {
  // Remove some loggers. This is a weak form of protection where he don't send some
  // loggers to the client, e.g. security related logs
  private val blackListedLoggers = List(""".*\.security\..*""".r)

  override def append(event: ILoggingEvent): Unit =
    // Send a message to the clients if level is INFO or higher
    // We are outside the normal execution loop, thus we need to call unsafePerformSync directly
    if (
      event.getLevel.isGreaterOrEqual(Level.INFO) && !blackListedLoggers.exists(
        _.findFirstIn(event.getLoggerName).isDefined
      )
    )
      dispatcher.unsafeRunAndForget(
        logTopic.publish1(event)
      )
    else ()

}
