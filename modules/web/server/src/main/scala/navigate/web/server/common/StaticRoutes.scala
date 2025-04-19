// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.common

import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.Sync
import fs2.compression.Compression
import fs2.io.file.Files
import org.http4s.CacheDirective
import org.http4s.CacheDirective.*
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Response
import org.http4s.StaticFile
import org.http4s.dsl.io.*
import org.http4s.headers.`Cache-Control`
import org.http4s.server.middleware.GZip

import scala.concurrent.duration.*

class StaticRoutes[F[_]: {Sync, Compression, Files}]:
  private val NavigateUiBase = "/navigate-ui"

  private val OneYear: FiniteDuration = 365.days

  private val CacheHeaders: List[Header.ToRaw] = List(
    `Cache-Control`(NonEmptyList.of(`max-age`(OneYear)))
  )

  // Cache index pages for a short time to avoid stale content
  private val IndexCacheHeaders: List[Header.ToRaw] = List(
    `Cache-Control`(NonEmptyList.of(`max-age`(6.hours), `must-revalidate`))
  )

  // /assets/* files are fingerprinted and can be cached for a long time
  val immutable                                     = CacheDirective("immutable")
  private val AssetCacheHeaders: List[Header.ToRaw] = List(
    `Cache-Control`(NonEmptyList.of(`public`, `max-age`(OneYear), immutable))
  )

  def localFile(path: String, req: Request[F]): OptionT[F, Response[F]] =
    StaticFile.fromResource(NavigateUiBase + path, Some(req))

  extension (req: Request[F])
    def endsWith(exts: String*): Boolean = exts.exists(req.pathInfo.toString.endsWith)

    def serve(path: String, headers: List[Header.ToRaw]): F[Response[F]] =
      localFile(path, req)
        .map(_.putHeaders(headers*))
        .getOrElse(Response.notFound[F])

  private val supportedExtension = List(
    ".html",
    ".js",
    ".map",
    ".css",
    ".png",
    ".jpg",
    ".eot",
    ".svg",
    ".woff",
    ".woff2",
    ".ttf",
    ".mp3",
    ".ico",
    ".webm",
    ".json"
  )

  def service: HttpRoutes[F] = GZip:
    HttpRoutes.of[F]:
      case req @ GET -> Root                                                  =>
        req.serve("/index.html", IndexCacheHeaders)
      case req @ GET -> "assets" /: rest if req.endsWith(supportedExtension*) =>
        req.serve(req.pathInfo.toString, AssetCacheHeaders)
      case req if req.endsWith(supportedExtension*)                           =>
        req.serve(req.pathInfo.toString, CacheHeaders)
      // This maybe not desired in all cases but it helps to keep client side routing cleaner
      case req if !req.pathInfo.toString.contains(".")                        =>
        req.serve("/index.html", IndexCacheHeaders)
