// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model.boopickle

import boopickle.CompositePickler
import boopickle.Default.UUIDPickler
import boopickle.Default.compositePickler
import boopickle.Default.doublePickler
import boopickle.Default.generatePickler
import boopickle.Default.intPickler
import boopickle.Default.longPickler
import boopickle.Default.optionPickler
import boopickle.Default.stringPickler
import boopickle.Default.transformPickler
import boopickle.Pickler
import cats._
import cats.syntax.all._
import engage.model.EngageEvent._
import engage.model._
import engage.model.enums.ServerLogLevel
import engage.model.security.UserDetails
import engage.model.security.UserLoginRequest
import eu.timepit.refined.api.RefType
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.util.Enumerated
import squants.time.Time
import squants.time.TimeConversions._

import java.time._

/**
 * Contains boopickle implicit picklers of model objects Boopickle can auto derive encoders but it
 * is preferred to make them explicitly
 */
trait ModelBooPicklers extends BooPicklerSyntax {
  implicit val yearPickler: Pickler[Year]           = transformPickler(Year.of)(_.getValue)
  implicit val localDatePickler: Pickler[LocalDate] =
    transformPickler(LocalDate.ofEpochDay)(_.toEpochDay)

  implicit val posLongPickler: Pickler[PosLong] =
    transformPickler[PosLong, Long]((l: Long) =>
      RefType
        .applyRef[PosLong](l)
        .getOrElse(throw new RuntimeException(s"Failed to decode value"))
    )(_.value)

  def valuesMap[F[_]: Traverse, A, B](c: F[A], f: A => B): Map[B, A] =
    c.fproduct(f).map(_.swap).toList.toMap

  def sourceIndex[A: Enumerated]: Map[Int, A] =
    Enumerated[A].all.zipWithIndex.map(_.swap).toMap

  def valuesMapPickler[A: Enumerated, B: Monoid: Pickler](
    valuesMap: Map[B, A]
  ): Pickler[A] =
    transformPickler((t: B) =>
      valuesMap
        .getOrElse(t, throw new RuntimeException(s"Failed to decode value"))
    )(t => valuesMap.find { case (_, v) => v === t }.foldMap(_._1))

  def enumeratedPickler[A: Enumerated]: Pickler[A] =
    valuesMapPickler[A, Int](sourceIndex[A])

  implicit val timeProgressPickler: Pickler[Time] =
    transformPickler((t: Double) => t.milliseconds)(_.toMilliseconds)

  implicit val userDetailsPickler: Pickler[UserDetails] = generatePickler[UserDetails]

  implicit val instantPickler: Pickler[Instant] =
    transformPickler((t: Long) => Instant.ofEpochMilli(t))(_.toEpochMilli)

  implicit val clientIdPickler: Pickler[ClientId] = generatePickler[ClientId]

  implicit val serverLogLevelPickler: Pickler[ServerLogLevel] = enumeratedPickler[ServerLogLevel]

  implicit val connectionOpenEventPickler: Pickler[ConnectionOpenEvent] =
    generatePickler[ConnectionOpenEvent]
  implicit val serverLogMessagePickler: Pickler[ServerLogMessage]       =
    generatePickler[ServerLogMessage]

  // Composite pickler for the engage event hierarchy
  implicit val eventsPickler: CompositePickler[EngageEvent] = compositePickler[EngageEvent]
    .addConcreteType[ConnectionOpenEvent]
    .addConcreteType[ServerLogMessage]
    .addConcreteType[NullEvent.type]

  implicit val userLoginPickler: Pickler[UserLoginRequest] = generatePickler[UserLoginRequest]

}
