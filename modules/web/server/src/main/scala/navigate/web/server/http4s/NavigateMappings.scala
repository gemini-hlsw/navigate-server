// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.Applicative
import cats.data.Ior
import cats.data.NonEmptyChain
import cats.effect.Sync
import cats.effect.syntax.all.*
import cats.syntax.all.*
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
import edu.gemini.grackle.Value
import edu.gemini.grackle.Value.BooleanValue
import edu.gemini.grackle.Value.FloatValue
import edu.gemini.grackle.Value.IntValue
import edu.gemini.grackle.Value.ObjectValue
import edu.gemini.grackle.Value.StringValue
import edu.gemini.grackle.circe.CirceMapping
import edu.gemini.schema.util.SchemaStitcher
import edu.gemini.schema.util.SourceResolver
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*
import lucuma.core.math.Coordinates
import lucuma.core.math.Declination
import lucuma.core.math.Epoch
import lucuma.core.math.Parallax
import lucuma.core.math.ProperMotion
import lucuma.core.math.RadialVelocity
import lucuma.core.math.RightAscension
import lucuma.core.math.Wavelength
import navigate.server.NavigateEngine
import navigate.server.tcs.AutoparkAowfs
import navigate.server.tcs.AutoparkGems
import navigate.server.tcs.AutoparkOiwfs
import navigate.server.tcs.AutoparkPwfs1
import navigate.server.tcs.AutoparkPwfs2
import navigate.server.tcs.FollowStatus
import navigate.server.tcs.ParkStatus
import navigate.server.tcs.ResetPointing
import navigate.server.tcs.ShortcircuitMountFilter
import navigate.server.tcs.ShortcircuitTargetFilter
import navigate.server.tcs.SlewConfig
import navigate.server.tcs.SlewOptions
import navigate.server.tcs.StopGuide
import navigate.server.tcs.Target
import navigate.server.tcs.ZeroChopThrow
import navigate.server.tcs.ZeroGuideOffset
import navigate.server.tcs.ZeroInstrumentOffset
import navigate.server.tcs.ZeroMountDiffTrack
import navigate.server.tcs.ZeroMountOffset
import navigate.server.tcs.ZeroSourceDiffTrack
import navigate.server.tcs.ZeroSourceOffset
import org.typelevel.log4cats.Logger
import spire.math.Algebraic.Expr.Sub

import java.nio.file.Path as JPath
import scala.io.Source
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.tools.nsc.doc.base.comment.ColumnOption
import scala.util.Using

class NavigateMappings[F[_]: Sync](server: NavigateEngine[F])(override val schema: Schema)
    extends CirceMapping[F] {
  import NavigateMappings._

  def mountPark(p: Path, env: Cursor.Env): F[Result[OperationOutcome]] =
    server.mcsPark.attempt
      .map(x =>
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success).rightIor
      )

  def mountFollow(p: Path, env: Cursor.Env): F[Result[OperationOutcome]] =
    env
      .get[Boolean]("enable")
      .map { en =>
        server
          .mcsFollow(en)
          .attempt
          .map(x =>
            x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
              .rightIor
          )
      }
      .getOrElse(
        Ior.Left(NonEmptyChain(Problem("mountFollow parameter could not be parsed."))).pure[F]
      )

  def rotatorPark(p: Path, env: Cursor.Env): F[Result[OperationOutcome]] =
    server.rotPark.attempt
      .map(x =>
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success).rightIor
      )

  def rotatorFollow(p: Path, env: Cursor.Env): F[Result[OperationOutcome]] =
    env
      .get[Boolean]("enable")
      .map { en =>
        server
          .rotFollow(en)
          .attempt
          .map(x =>
            x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
              .rightIor
          )
      }
      .getOrElse(
        Ior.Left(NonEmptyChain(Problem("rotatorFollow parameter could not be parsed."))).pure[F]
      )

  def slew(p: Path, env: Cursor.Env): F[Result[OperationOutcome]] =
    env
      .get[SlewConfig]("slewParams")(classTag[SlewConfig])
      .map { sc =>
        server
          .slew(sc)
          .attempt
          .map(x =>
            x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
              .rightIor
          )
      }
      .getOrElse(Ior.Left(NonEmptyChain(Problem("Slew parameters could not be parsed."))).pure[F])

  val MutationType: TypeRef         = schema.ref("Mutation")
  val ParkStatusType: TypeRef       = schema.ref("ParkStatus")
  val FollowStatusType: TypeRef     = schema.ref("FollowStatus")
  val OperationOutcomeType: TypeRef = schema.ref("OperationOutcome")
  val OperationResultType: TypeRef  = schema.ref("OperationResult")

  override val selectElaborator: SelectElaborator = new SelectElaborator(
    Map(
      MutationType -> {
        case Select("mountFollow", List(Binding("enable", BooleanValue(en))), child)   =>
          Environment(
            Cursor.Env("enable" -> en),
            Select("mountFollow", Nil, child)
          ).rightIor
        case Select("rotatorFollow", List(Binding("enable", BooleanValue(en))), child) =>
          Environment(
            Cursor.Env("enable" -> en),
            Select("rotatorFollow", Nil, child)
          ).rightIor
        case Select("slew", List(Binding("slewParams", ObjectValue(fields))), child)   =>
          Result.fromOption(
            parseSlewConfigInput(fields).map { x =>
              Environment(
                Cursor.Env("slewParams" -> x),
                Select("slew", Nil, child)
              )
            },
            "Could not parse Slew parameters."
          )
      }
    )
  )

  override val typeMappings: List[TypeMapping] = List(
    ObjectMapping(
      tpe = MutationType,
      fieldMappings = List(
        RootEffect.computeEncodable("mountPark")((_, p, env) => mountPark(p, env)),
        RootEffect.computeEncodable("mountFollow")((_, p, env) => mountFollow(p, env)),
        RootEffect.computeEncodable("rotatorPark")((_, p, env) => rotatorPark(p, env)),
        RootEffect.computeEncodable("rotatorFollow")((_, p, env) => rotatorFollow(p, env)),
        RootEffect.computeEncodable("slew")((_, p, env) => slew(p, env))
      )
    ),
    LeafMapping[ParkStatus](ParkStatusType),
    LeafMapping[FollowStatus](FollowStatusType),
    LeafMapping[OperationOutcome](OperationOutcomeType),
    LeafMapping[OperationResult](OperationResultType)
  )
}

object NavigateMappings extends GrackleParsers {

  def loadSchema[F[_]: Sync]: F[Schema] = SchemaStitcher
    .apply[F](JPath.of("NewTCC.graphql"), SourceResolver.fromResource(getClass.getClassLoader))
    .build
    .map(_.right.get)

  def apply[F[_]: Sync](server: NavigateEngine[F]): F[NavigateMappings[F]] = loadSchema.map {
    schema =>
      new NavigateMappings[F](server)(schema)
  }

  def parseSlewOptionsInput(l: List[(String, Value)]): Option[SlewOptions] = for {
    zct  <-
      l.collectFirst { case ("zeroChopThrow", BooleanValue(v)) => v }.map(ZeroChopThrow.value(_))
    zso  <- l.collectFirst { case ("zeroSourceOffset", BooleanValue(v)) => v }
              .map(ZeroSourceOffset.value(_))
    zsdt <- l.collectFirst { case ("zeroSourceDiffTrack", BooleanValue(v)) => v }
              .map(ZeroSourceDiffTrack.value(_))
    zmo  <- l.collectFirst { case ("zeroMountOffset", BooleanValue(v)) => v }
              .map(ZeroMountOffset.value(_))
    zmdt <- l.collectFirst { case ("zeroMountDiffTrack", BooleanValue(v)) => v }
              .map(ZeroMountDiffTrack.value(_))
    stf  <- l.collectFirst { case ("shortcircuitTargetFilter", BooleanValue(v)) => v }
              .map(ShortcircuitTargetFilter.value(_))
    smf  <- l.collectFirst { case ("shortcircuitMountFilter", BooleanValue(v)) => v }
              .map(ShortcircuitMountFilter.value(_))
    rp   <-
      l.collectFirst { case ("resetPointing", BooleanValue(v)) => v }.map(ResetPointing.value(_))
    sg   <- l.collectFirst { case ("stopGuide", BooleanValue(v)) => v }.map(StopGuide.value(_))
    zgo  <- l.collectFirst { case ("zeroGuideOffset", BooleanValue(v)) => v }
              .map(ZeroGuideOffset.value(_))
    zio  <- l.collectFirst { case ("zeroInstrumentOffset", BooleanValue(v)) => v }
              .map(ZeroInstrumentOffset.value(_))
    ap1  <-
      l.collectFirst { case ("autoparkPwfs1", BooleanValue(v)) => v }.map(AutoparkPwfs1.value(_))
    ap2  <-
      l.collectFirst { case ("autoparkPwfs2", BooleanValue(v)) => v }.map(AutoparkPwfs2.value(_))
    ao   <-
      l.collectFirst { case ("autoparkOiwfs", BooleanValue(v)) => v }.map(AutoparkOiwfs.value(_))
    ag   <- l.collectFirst { case ("autoparkGems", BooleanValue(v)) => v }.map(AutoparkGems.value(_))
    aa   <-
      l.collectFirst { case ("autoparkAowfs", BooleanValue(v)) => v }.map(AutoparkAowfs.value(_))
  } yield SlewOptions(
    zct,
    zso,
    zsdt,
    zmo,
    zmdt,
    stf,
    smf,
    rp,
    sg,
    zgo,
    zio,
    ap1,
    ap2,
    ao,
    ag,
    aa
  )

  def parseSiderealTarget(
    name:         String,
    centralWavel: Wavelength,
    l:            List[(String, Value)]
  ): Option[Target.SiderealTarget] = for {
    ra    <- l.collectFirst { case ("ra", ObjectValue(v)) => parseRightAscension(v) }.flatten
    dec   <- l.collectFirst { case ("dec", ObjectValue(v)) => parseDeclination(v) }.flatten
    epoch <- l.collectFirst { case ("epoch", StringValue(v)) => parseEpoch(v) }.flatten

  } yield Target.SiderealTarget(
    name,
    centralWavel,
    Coordinates(ra, dec),
    epoch,
    l.collectFirst { case ("properMotion", ObjectValue(v)) => parseProperMotion(v) }.flatten,
    l.collectFirst { case ("radialVelocity", ObjectValue(v)) => parseRadialVelocity(v) }.flatten,
    l.collectFirst { case ("parallax", ObjectValue(v)) => parseParallax(v) }.flatten
  )

  def parseNonSiderealTarget(
    name: String,
    w:    Wavelength,
    l:    List[(String, Value)]
  ): Option[Target.SiderealTarget] = none

  def parseEphemerisTarget(
    name: String,
    w:    Wavelength,
    l:    List[(String, Value)]
  ): Option[Target.EphemerisTarget] = none

  def parseBaseTarget(l: List[(String, Value)]): Option[Target] = for {
    nm <- l.collectFirst { case ("name", StringValue(v)) => v }
    wv <- l.collectFirst { case ("wavelength", ObjectValue(v)) => parseWavelength(v) }.flatten
    bt <- l.collectFirst { case ("sidereal", ObjectValue(v)) => v }
            .flatMap[Target](parseSiderealTarget(nm, wv, _))
            .orElse(
              l.collectFirst { case ("nonsidereal", ObjectValue(v)) => v }
                .flatMap(parseNonSiderealTarget(nm, wv, _))
            )
  } yield bt

  def parseSlewConfigInput(l: List[(String, Value)]): Option[SlewConfig] = for {
    sol <- l.collectFirst { case ("slewOptions", ObjectValue(v)) => v }
    so  <- parseSlewOptionsInput(sol)
    tl  <- l.collectFirst { case ("baseTarget", ObjectValue(v)) => v }
    t   <- parseBaseTarget(tl)
  } yield SlewConfig(so, t)

}