// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.parse.Parser
import cats.parse.Parser0
import cats.parse.Rfc5234.char
import cats.syntax.all.*
import lucuma.core.enums.LightSinkName
import lucuma.core.enums.parser.EnumParsers
import lucuma.core.parser.MiscParsers.int
import navigate.model.enums.LightSource
import navigate.model.enums.LightSource.*
import navigate.server.acm.Decoder
import navigate.server.acm.Encoder
import navigate.server.tcs.ScienceFold.Parked
import navigate.server.tcs.ScienceFold.Position

// Decoding and encoding the science fold position require some common definitions, therefore I
// put them inside an object
private[server] trait ScienceFoldPositionCodex:

  private val AO_PREFIX   = "ao2"
  private val GCAL_PREFIX = "gcal2"
  private val PARK_POS    = "park-pos"

  val lightSink: Parser[LightSinkName] = EnumParsers.enumBy[LightSinkName](_.name)

  def prefixed(p: String, s: LightSource): Parser0[ScienceFold] =
    (Parser.string0(p) *> lightSink ~ int)
      .map: (ls, port) =>
        Position(s, ls, port)
      .widen[ScienceFold]

  val park: Parser[ScienceFold] =
    (Parser.string(PARK_POS) <* char.rep.?).as(Parked)

  given Decoder[String, Option[ScienceFold]] = (t: String) =>
    (park | prefixed(AO_PREFIX, AO) | prefixed(GCAL_PREFIX, GCAL) | prefixed("", Sky))
      .parseAll(t)
      .toOption

  given Encoder[Position, String] = (a: Position) =>
    val instAGName = if (a.sink === LightSinkName.Ac) a.sink.name else s"${a.sink.name}${a.port}"

    a.source match {
      case Sky  => instAGName
      case AO   => AO_PREFIX + instAGName
      case GCAL => GCAL_PREFIX + instAGName
    }

  extension (a: Position) {
    def encode: String = Encoder.encode(a)
  }

object ScienceFoldPositionCodex extends ScienceFoldPositionCodex
