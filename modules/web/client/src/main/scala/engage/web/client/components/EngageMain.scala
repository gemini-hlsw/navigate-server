// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.components

import cats.syntax.all._
import engage.web.client.model.Pages._
import engage.web.client.reusability._
import japgolly.scalajs.react.React
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.enums.Site
import react.common._
import react.common.implicits._
import react.semanticui.elements.divider.Divider

import scala.language.implicitConversions

final case class AppTitle(site: Site) extends ReactProps[AppTitle, Unit, Unit](AppTitle.component)

object AppTitle {
  type Props = AppTitle

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]

  private val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P(p =>
      Divider(as = "h4",
              horizontal = true,
              clazz = EngageStyles.titleRow |+| EngageStyles.notInMobile |+| EngageStyles.header
      )(
        s"Engage ${p.site.shortName}"
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}

final case class EngageMain(site: Site, ctl: RouterCtl[EngagePages])
    extends ReactProps[EngageMain, Unit, Unit](EngageMain.component)

object EngageMain {
  type Props = EngageMain

  implicit val propsReuse: Reusability[Props] = Reusability.by(_.site)

  private val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P(p =>
      React.Fragment(
        <.div(EngageStyles.MainUI)(
          AppTitle(p.site)
        )
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}
