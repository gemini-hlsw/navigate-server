// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.acm

import cats.syntax.all.*
import lucuma.core.util.Enumerated

trait Encoder[A, B] {
  def encode(a: A): B
}

object Encoder {

  given [A]: Encoder[A, A] = (a: A) => a

  given Encoder[Int, String] = (a: Int) => s"$a"

  given Encoder[Double, String] = (a: Double) => s"$a"

  given [A: Enumerated]: Encoder[A, String] = (a: A) => Enumerated[A].tag(a)

  given [A: Encoder[*, String]]: Encoder[Option[A], String] = (x: Option[A]) =>
    x.foldMap(_.encode[String])

  extension [A](a: A) {
    def encode[B](using enc: Encoder[A, B]): B = enc.encode(a)
  }

}
