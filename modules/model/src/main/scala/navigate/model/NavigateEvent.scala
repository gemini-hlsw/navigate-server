// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model

import cats.Eq
import cats.Order
import cats.syntax.all.*
import lucuma.core.model.User
import navigate.model.enums.ServerLogLevel

import java.time.Instant

sealed trait NavigateEvent

object NavigateEvent {
  case class ServerLogMessage(level: ServerLogLevel, timestamp: Instant, msg: String)
      extends NavigateEvent
  object ServerLogMessage {
    private given Order[Instant]  =
      Order.by(_.getNano)
    given Order[ServerLogMessage] =
      Order.by(x => (x.level, x.timestamp, x.msg))
  }

  case object NullEvent extends NavigateEvent

  case class ConnectionOpenEvent(
    userDetails:   Option[User],
    clientId:      ClientId,
    serverVersion: String
  ) extends NavigateEvent

  object ConnectionOpenEvent {
    given Eq[ConnectionOpenEvent] =
      Eq.by(x => (x.userDetails, x.clientId, x.serverVersion))
  }

  case class CommandStart(cmd: NavigateCommand) extends NavigateEvent
  object CommandStart {
    given Eq[CommandStart] = Eq.by(_.cmd)
  }

  case class CommandSuccess(cmd: NavigateCommand) extends NavigateEvent
  object CommandSuccess {
    given Eq[CommandSuccess] = Eq.by(_.cmd)
  }

  case class CommandPaused(cmd: NavigateCommand) extends NavigateEvent
  object CommandPaused {
    given Eq[CommandPaused] = Eq.by(_.cmd)
  }

  case class CommandFailure(cmd: NavigateCommand, msg: String) extends NavigateEvent
  object CommandFailure {
    given Eq[CommandFailure] = Eq.by(x => (x.cmd, x.msg))
  }

  given Eq[NavigateEvent] =
    Eq.instance {
      case (a: ServerLogMessage, b: ServerLogMessage)       => a === b
      case (a: ConnectionOpenEvent, b: ConnectionOpenEvent) => a === b
      case (NullEvent, NullEvent)                           => true
      case (a: CommandStart, b: CommandStart)               => a === b
      case (a: CommandFailure, b: CommandFailure)           => a === b
      case (a: CommandSuccess, b: CommandSuccess)           => a === b
      case (a: CommandPaused, b: CommandPaused)             => a === b
      case _                                                => false
    }

  sealed trait ForClient extends NavigateEvent {
    def clientId: ClientId
  }

}
