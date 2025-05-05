// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.config

import cats.Eq
import cats.derived.*
import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

trait GpiSettings

/**
 * Configuration of the Navigate Engine
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
case class NavigateEngineConfiguration(
  odb:                     Uri,
  observe:                 Uri,
  systemControl:           SystemsControlConfiguration,
  odbNotifications:        Boolean,
  odbQueuePollingInterval: FiniteDuration,
  tops:                    String,
  epicsCaAddrList:         Option[String],
  readRetries:             Int,
  ioTimeout:               FiniteDuration
) derives Eq
