// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.schema.util

import cats.effect.MonadCancelThrow
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all.*

import java.nio.file.Path
import scala.io.Source

// Trait used to find the schema file
trait SourceResolver[F[_]] {
  def resolve(name: Path): Resource[F, Source]
}

object SourceResolver {

  def fromResource[F[_]: Sync](classLoader: ClassLoader): SourceResolver[F] = (name: Path) =>
    Resource.fromAutoCloseable(Sync[F].blocking(Source.fromResource(name.toString, classLoader)))

  def fromString[F[_]: Sync](name: Path, content: String): SourceResolver[F] = (n: Path) =>
    if (n === name) Resource.pure(Source.fromString(content))
    else Resource.raiseError[F, Source, Throwable](new Error(s"Unknown source $n"))

  def fromStringMap[F[_]: Sync](m: Map[Path, String]): SourceResolver[F] = (name: Path) =>
    m.get(name)
      .map(x => Resource.pure(Source.fromString(x)))
      .getOrElse(Resource.raiseError[F, Source, Throwable](new Error(s"Unknown source $name")))

  extension [F[_]: MonadCancelThrow](s: SourceResolver[F])
    def or(other: SourceResolver[F]): SourceResolver[F] = (name: Path) =>
      s.resolve(name).handleErrorWith[Source](_ => other.resolve(name))

}
