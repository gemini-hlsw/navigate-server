// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.config

import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.Site
import lucuma.sso.client.util.GpgPublicKeyReader
import navigate.model.config.*
import pureconfig.*
import pureconfig.error.*
import pureconfig.module.catseffect.syntax.*
import pureconfig.module.http4s.*
import pureconfig.module.ip4s.*

import java.security.PublicKey

case class SiteValueUnknown(site: String)         extends FailureReason {
  def description: String = s"site '$site' invalid"
}
case class ModeValueUnknown(mode: String)         extends FailureReason {
  def description: String = s"mode '$mode' invalid"
}
case class StrategyValueUnknown(strategy: String) extends FailureReason {
  def description: String = s"strategy '$strategy' invalid"
}
case class PublicKeyUnknown(value: String)        extends FailureReason {
  def description: String = s"publicKey '$value' invalid"
}

given ConfigReader[PublicKey] =
  ConfigReader.fromCursor[PublicKey] { cf =>
    cf.asString.flatMap { c =>
      GpgPublicKeyReader
        .publicKey(c)
        .leftFlatMap(_ => cf.failed(PublicKeyUnknown(c)))
    }
  }

given ConfigReader[Site] = ConfigReader.fromCursor[Site] { cf =>
  cf.asString.flatMap {
    case "GS" => Site.GS.asRight
    case "GN" => Site.GN.asRight
    case s    => cf.failed(SiteValueUnknown(s))
  }
}

given ConfigReader[Mode] = ConfigReader.fromCursor[Mode] { cf =>
  cf.asString.flatMap {
    case "production" => Mode.Production.asRight
    case "dev"        => Mode.Development.asRight
    case s            => cf.failed(ModeValueUnknown(s))
  }
}

given ConfigReader[ControlStrategy] =
  ConfigReader.fromCursor[ControlStrategy] { cf =>
    cf.asString.flatMap { c =>
      ControlStrategy.fromString(c) match {
        case Some(x) => x.asRight
        case _       => cf.failed(StrategyValueUnknown(c))
      }
    }
  }

given ConfigReader[TLSConfig] = ConfigReader.derived

def loadConfiguration[F[_]: Sync](config: ConfigObjectSource): F[NavigateConfiguration] = {
  given ConfigReader[NavigateConfiguration] =
    ConfigReader.derived[NavigateConfiguration]
  config.loadF[F, NavigateConfiguration]()
}
