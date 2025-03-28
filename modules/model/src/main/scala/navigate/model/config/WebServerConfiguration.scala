// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.config

import cats.Eq
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import org.http4s.Uri

import java.nio.file.Path

/**
 * Configuration for the TLS server
 * @param keyStore
 *   Location where to find the keystore
 * @param keyStorePwd
 *   Password for the keystore
 * @param certPwd
 *   Password for the certificate used for TLS
 */
case class TLSConfig(keyStore: Path, keyStorePwd: String, certPwd: String)

object TLSConfig {

  given Eq[Path] = Eq.fromUniversalEquals

  given Eq[TLSConfig] =
    Eq.by(x => (x.keyStore, x.keyStorePwd, x.certPwd))
}

/**
 * Configuration for the web server side of the navigate
 * @param host
 *   Host name to listen, typically 0.0.0.0
 * @param port
 *   Port to listen for web requests
 * @param insecurePort
 *   Port where we setup a redirect server to send to https
 * @param externalBaseUrl
 *   Redirects need an external facing name
 * @param tls
 *   Configuration of TLS, optional
 */
case class WebServerConfiguration(
  host:            Host,
  port:            Port,
  insecurePort:    Port,
  externalBaseUrl: String,
  proxyBaseUri:    Uri,
  tls:             Option[TLSConfig] = None
)

object WebServerConfiguration {
  given Eq[WebServerConfiguration] =
    Eq.by(x => (x.host, x.port, x.insecurePort, x.externalBaseUrl, x.tls))
}
