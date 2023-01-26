// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.http4s

import cats.Applicative
import cats.data.NonEmptyChain
import cats.effect.Sync
import cats.effect.syntax.all.*
import cats.syntax.all.*
import cats.syntax.either.catsSyntaxEither
import edu.gemini.grackle.Cursor
import edu.gemini.grackle.Mapping
import edu.gemini.grackle.Path
import edu.gemini.grackle.Predicate.Eql
import edu.gemini.grackle.Problem
import edu.gemini.grackle.Query
import edu.gemini.grackle.Query.Binding
import edu.gemini.grackle.Query.Environment
import edu.gemini.grackle.Query.Filter
import edu.gemini.grackle.Query.Select
import edu.gemini.grackle.Query.Unique
import edu.gemini.grackle.QueryCompiler.SelectElaborator
import edu.gemini.grackle.Result
import edu.gemini.grackle.Schema
import edu.gemini.grackle.TypeRef
import edu.gemini.grackle.Value.BooleanValue
import edu.gemini.grackle.ValueMapping
import engage.server.EngageEngine
import engage.server.tcs.FollowStatus
import engage.server.tcs.ParkStatus
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
import spire.math.Algebraic.Expr.Sub

import scala.io.Source
import scala.util.Using

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

  override val selectElaborator: SelectElaborator = new SelectElaborator(
    Map(
      MutationType -> {
        case Select("mountFollow", List(Binding("enable", BooleanValue(en))), child) =>
          Environment(
            Cursor.Env("enable" -> en),
            Select("mountFollow", Nil, child)
          ).rightIor
      }
    )
  )

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
    Using(Source.fromResource("NewTCC.graphql", getClass.getClassLoader)) { src =>
      Schema(src.mkString).right.get
    }.liftTo[F]
  }

  def apply[F[_]: Sync](server: EngageEngine[F]): F[EngageMappings[F]] = loadSchema.map { schema =>
    new EngageMappings[F](server)(schema)
  }
}
