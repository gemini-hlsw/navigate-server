// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

sealed trait NavigateFailure extends Exception with Product with Serializable

object NavigateFailure {

  /** Something went wrong while applying a configuration. * */
  case class Execution(errMsg: String) extends NavigateFailure

  case class Unexpected(msg: String) extends NavigateFailure

  case class Timeout(msg: String) extends NavigateFailure

  case class NullEpicsError(channel: String) extends NavigateFailure

  def explain(f: NavigateFailure): String = f match {
    case Execution(errMsg)       => s"Configuration action failed with error: $errMsg"
    case Unexpected(msg)         => s"Unexpected error: $msg"
    case Timeout(msg)            => s"Timeout while waiting for $msg"
    case NullEpicsError(channel) => s"Failed to read epics channel: $channel"
  }

}
