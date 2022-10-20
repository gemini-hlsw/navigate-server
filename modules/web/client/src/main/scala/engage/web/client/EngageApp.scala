// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client

import cats.effect.Sync
import cats.effect._
import engage.web.client.components.EngageUI
import lucuma.core.enums.Site
import org.scalajs.dom.Element
import org.scalajs.dom.document
import typings.loglevel.mod.{^ => logger}

import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

/**
 * Engage WebApp entry point
 */
final class EngageLauncher[F[_]](implicit val F: Sync[F]) {
  // japgolly.scalajs.react.extra.ReusabilityOverlay.overrideGloballyInDev()

  def serverSite: F[Site] = F.pure(Site.GS)

  def renderingNode: F[Element] =
    F.delay {
      // Find or create the node where we render
      Option(document.getElementById("root")).getOrElse {
        val elem = document.createElement("div")
        elem.id = "root"
        document.body.appendChild(elem)
        elem
      }
    }

}

/**
 * Engage WebApp entry point Exposed to the js world
 */
@JSExportTopLevel("EngageApp")
object EngageApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val launcher = new EngageLauncher[IO]
    // Render the UI using React
    for {
      engageSite <- launcher.serverSite
      router     <- EngageUI.router[IO](engageSite)
      node       <- launcher.renderingNode
      _          <- IO(router().renderIntoDOM(node)).handleErrorWith(p => IO(logger.error(p.toString)))
    } yield ExitCode.Success
  }

  @JSExport
  def stop(): Unit = ()

  @JSExport
  def start(): Unit =
    super.main(Array())

}
