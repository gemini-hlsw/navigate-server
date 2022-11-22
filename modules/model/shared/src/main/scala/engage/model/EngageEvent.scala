// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model

import cats.Eq
import cats.Order
import cats.syntax.all._
import engage.model.enums.ServerLogLevel
import engage.model.security.UserDetails

import java.time.Instant

sealed trait EngageEvent

object EngageEvent {
  final case class ServerLogMessage(level: ServerLogLevel, timestamp: Instant, msg: String)
      extends EngageEvent
  object ServerLogMessage {
    private implicit val instantOrder: Order[Instant]           =
      Order.by(_.getNano)
    implicit val serverLogMessageOrder: Order[ServerLogMessage] =
      Order.by(x => (x.level, x.timestamp, x.msg))
  }

  case object NullEvent extends EngageEvent

  final case class ConnectionOpenEvent(
    userDetails:   Option[UserDetails],
    clientId:      ClientId,
    serverVersion: String
  ) extends EngageEvent

  object ConnectionOpenEvent {
    implicit lazy val equal: Eq[ConnectionOpenEvent] =
      Eq.by(x => (x.userDetails, x.clientId, x.serverVersion))
  }

  final case class CommandStart(cmd: EngageCommand) extends EngageEvent
  object CommandStart {
    implicit val commandStartEq: Eq[CommandStart] = Eq.by(_.cmd)
  }

  final case class CommandSuccess(cmd: EngageCommand) extends EngageEvent
  object CommandSuccess {
    implicit val commandSuccessEq: Eq[CommandSuccess] = Eq.by(_.cmd)
  }

  final case class CommandPaused(cmd: EngageCommand) extends EngageEvent
  object CommandPaused {
    implicit val commandPausedEq: Eq[CommandPaused] = Eq.by(_.cmd)
  }

  final case class CommandFailure(cmd: EngageCommand, msg: String) extends EngageEvent
  object CommandFailure {
    implicit val commandFailureEq: Eq[CommandFailure] = Eq.by(x => (x.cmd, x.msg))
  }

  implicit val equal: Eq[EngageEvent] =
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

  sealed trait ForClient extends EngageEvent {
    def clientId: ClientId
  }

}
