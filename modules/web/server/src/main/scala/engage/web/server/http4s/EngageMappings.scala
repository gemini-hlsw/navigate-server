// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.http4s

import cats.Applicative
import cats.data.NonEmptyChain
import cats.effect.Sync
import cats.effect.syntax.all.*
import cats.syntax.all.*
import cats.syntax.either.catsSyntaxEither
import org.typelevel.log4cats.Logger
import edu.gemini.grackle.{
  Cursor,
  Mapping,
  Path,
  Problem,
  Query,
  Result,
  Schema,
  TypeRef,
  ValueMapping
}
import engage.server.EngageEngine
import engage.server.tcs.{FollowStatus, ParkStatus}
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax.*
import spire.math.Algebraic.Expr.Sub

import scala.util.Using
import scala.io.Source

class EngageMappings[F[_]: Sync](server: EngageEngine[F])(override val schema: Schema)
    extends ValueMapping[F] {
  import EngageMappings._

  def mountPark(p: Path, env: Cursor.Env): F[Result[Cursor]] =
    server.mcsPark.attempt.map(
      _.bimap(
        e => NonEmptyChain(Problem(e.getMessage)),
        _ => valueCursor(p, env, ParkStatus.Parked)
      ).toIor
    )

  def mountFollow(p: Path, env: Cursor.Env): F[Result[Cursor]] =
    env
      .get[Boolean]("enable")
      .map(server.mcsFollow)
      .getOrElse(Applicative[F].unit)
      .attempt
      .map(
        _.bimap(
          e => NonEmptyChain(Problem(e.getMessage)),
          _ => valueCursor(p, env, FollowStatus.Following)
        ).toIor
      )

  val MutationType: TypeRef     = schema.ref("Mutation")
  val ParkStatusType: TypeRef   = schema.ref("ParkStatus")
  val FollowStatusType: TypeRef = schema.ref("FollowStatus")

  override val typeMappings: List[TypeMapping] = List(
    ObjectMapping(
      tpe = MutationType,
      fieldMappings = List(
        RootEffect.computeCursor("mountPark")((_, p, env) => mountPark(p, env)),
        RootEffect.computeCursor("mountFollow")((_, p, env) => mountFollow(p, env))
      )
    ),
    LeafMapping[ParkStatus](ParkStatusType),
    LeafMapping[FollowStatus](FollowStatusType)
  )
}

object EngageMappings {

  def loadSchema[F[_]: Sync]: F[Schema] = Sync[F].defer {
    Using(Source.fromResource("newTCC.graphql", getClass.getClassLoader)) { src =>
      Schema(src.mkString).right.get
    }.liftTo[F]
  }

  def apply[F[_]: Sync](server: EngageEngine[F]): F[EngageMappings[F]] = loadSchema.map { schema =>
    new EngageMappings[F](server)(schema)
  }
}
