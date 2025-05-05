// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.config

import cats.Eq
import lucuma.core.enums.Site

/**
 * Top configuration of the navigate
 * @param site
 *   Site this navigate instance handles (GN/GS)
 * @param mode
 *   Execution mode
 * @param navigateEngine
 *   Configuration of the engine
 * @param webServer
 *   Web side configuration
 * @param authentication
 *   Configuration to support authentication
 */
case class NavigateConfiguration(
  site:           Site,
  mode:           Mode,
  navigateEngine: NavigateEngineConfiguration,
  lucumaSSO:      LucumaSSOConfiguration,
  webServer:      WebServerConfiguration
)

object NavigateConfiguration {
  given Eq[NavigateConfiguration] =
    Eq.by(x => (x.site, x.mode, x.navigateEngine, x.webServer))
}
