// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model.config

import cats.Eq
import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

trait GpiSettings

/**
 * Configuration of the Engage Engine
 * @param odb
 *   Location of the odb server
 * @param systemControl
 *   Control of the subsystems
 * @param odbNotifications
 *   Indicates if we notify the odb of sequence events
 * @param odbQueuePollingInterval
 *   frequency to check the odb queue
 * @param gpiUrl
 *   URL for the GPI GMP
 * @param tops
 *   Used to select the top component for epics subsystems
 * @param epicsCaAddrList
 *   List of IPs for the epics subsystem
 * @param readRetries
 *   Number of retries when reading a channel
 * @param ioTimeout
 *   Timeout to listen for EPICS events
 */
final case class EngageEngineConfiguration(
  odb:                     Uri,
  systemControl:           SystemsControlConfiguration,
  odbNotifications:        Boolean,
  odbQueuePollingInterval: FiniteDuration,
  tops:                    String,
  epicsCaAddrList:         Option[String],
  readRetries:             Int,
  ioTimeout:               FiniteDuration
)

object EngageEngineConfiguration {

  implicit val eqEngageEngineConfiguration: Eq[EngageEngineConfiguration] =
    Eq.by(x =>
      (x.odb,
       x.systemControl,
       x.odbNotifications,
       x.odbQueuePollingInterval,
       x.tops,
       x.epicsCaAddrList,
       x.readRetries,
       x.ioTimeout
      )
    )

}
