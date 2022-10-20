// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.components

import cats.effect.Sync
import cats.syntax.all._
import engage.web.client.model.Pages.EngagePages
import engage.web.client.model.Pages.Root
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.enums.Site

/**
 * UI Router
 */
object EngageUI {
  private def pageTitle(site: Site): String = s"Engage - ${site.shortName}"

  def router[F[_]](site: Site)(implicit F: Sync[F]): F[Router[EngagePages]] = {

    val routerConfig = RouterConfigDsl[EngagePages].buildConfig { dsl =>
      import dsl._

      (emptyRule
        | staticRoute(root, Root) ~> renderR(r => EngageMain(site, r)))
        .notFound(redirectToPage(Root)(SetRouteVia.HistoryPush))
        // Runtime verification that all pages are routed
        .renderWith { case (_, r) => <.div(r.render()) }
        .setTitle(_ => pageTitle(site))
        .logToConsole
    }

    for {
      r          <- F.delay(Router.componentAndLogic(BaseUrl.fromWindowOrigin, routerConfig))
      (router, _) = r
    } yield router
  }

}
