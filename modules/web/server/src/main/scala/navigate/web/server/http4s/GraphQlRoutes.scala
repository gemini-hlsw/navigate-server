// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.effect.Async
import cats.effect.kernel.Ref
import cats.syntax.all.*
import ch.qos.logback.classic.spi.ILoggingEvent
import fs2.compression.Compression
import fs2.concurrent.Topic
import lucuma.graphql.routes.GraphQLService
import lucuma.graphql.routes.Routes
import natchez.Trace
import navigate.model.AcquisitionAdjustment
import navigate.server.NavigateEngine
import navigate.server.tcs.GuideState
import navigate.server.tcs.GuidersQualityValues
import navigate.server.tcs.TelescopeState
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.GZip
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger

class GraphQlRoutes[F[_]: Async: Logger: Trace: Compression](
  eng:                        NavigateEngine[F],
  logTopic:                   Topic[F, ILoggingEvent],
  guideStateTopic:            Topic[F, GuideState],
  guidersQualityTopic:        Topic[F, GuidersQualityValues],
  telescopeStateTopic:        Topic[F, TelescopeState],
  acquisitionAdjustmentTopic: Topic[F, AcquisitionAdjustment],
  logBuffer:                  Ref[F, Seq[ILoggingEvent]]
) extends Http4sDsl[F] {

  private def commandServices(wsb: WebSocketBuilder2[F]): HttpRoutes[F] = GZip(
    Routes.forService(
      _ =>
        NavigateMappings(eng,
                         logTopic,
                         guideStateTopic,
                         guidersQualityTopic,
                         telescopeStateTopic,
                         acquisitionAdjustmentTopic,
                         logBuffer
        )
          .map(
            GraphQLService[F](_).some
          ),
      wsb
    )
  )

  def service(wsb: WebSocketBuilder2[F]): HttpRoutes[F] =
    GZip(commandServices(wsb))

}
