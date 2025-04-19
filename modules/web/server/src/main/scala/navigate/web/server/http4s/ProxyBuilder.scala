// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.data.OptionT
import cats.effect.Async
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.io.net.Network
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.*

object ProxyBuilder:
  def buildService[F[_]: {Async, Network}](
    baseUri:   Uri,
    localPath: Uri.Path
  ): Resource[F, HttpRoutes[F]] =
    val remoteBaseHost: String = baseUri.host.map(_.toString).orEmpty
    val localPathElements: Int = localPath.segments.length

    EmberClientBuilder
      .default[F]
      .build
      .map(
        _.toHttpApp
          .mapK(OptionT.liftK) // Turns HttpApp into HttpRoutes
          .local: req =>
            req
              .withUri(
                // Drop the local path (eg: "/proxy")
                baseUri.resolve(req.uri.withPath(req.uri.path.splitAt(localPathElements)._2))
              )
              .putHeaders(Host(remoteBaseHost))
      )
