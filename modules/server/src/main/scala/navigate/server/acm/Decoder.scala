// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.acm

trait Decoder[B, A] {
  def decode(b: B): A
}

object Decoder {
  given [B]: Decoder[B, B] = (b: B) => b

  extension [B](b: B) {
    def decode[A](using d: Decoder[B, A]): A = d.decode(b)
  }
}
