// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.Eq
import cats.syntax.option.*
import io.circe.Encoder

import OperationResult.*

case class OperationOutcome(
  result: OperationResult,
  msg:    Option[String]
) derives Encoder.AsObject

object OperationOutcome {

  given Eq[OperationOutcome] = Eq.by(x => (x.result, x.msg))

  val success: OperationOutcome = OperationOutcome(Success, None)

  def failure(msg: Option[String]): OperationOutcome = OperationOutcome(Failure, msg)

  def failure: OperationOutcome = failure(None)

  def failure(msg: String): OperationOutcome = failure(msg.some)

}
