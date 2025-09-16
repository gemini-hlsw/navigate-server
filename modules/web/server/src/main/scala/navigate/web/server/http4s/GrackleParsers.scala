// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import algebra.instances.all.*
import cats.syntax.all.*
import coulomb.*
import coulomb.syntax.*
import grackle.Value
import grackle.Value.FloatValue
import grackle.Value.IntValue
import grackle.Value.ObjectValue
import grackle.Value.StringValue
import lucuma.core.math.Angle
import lucuma.core.math.Declination
import lucuma.core.math.Epoch
import lucuma.core.math.HourAngle
import lucuma.core.math.Parallax
import lucuma.core.math.ProperMotion
import lucuma.core.math.RadialVelocity
import lucuma.core.math.RightAscension
import lucuma.core.math.Wavelength
import lucuma.core.math.units.CentimetersPerSecond
import lucuma.core.math.units.MetersPerSecond
import lucuma.core.syntax.string.*
import lucuma.core.util.Enumerated
import lucuma.core.util.TimeSpan
import navigate.model.Distance

import scala.language.implicitConversions

trait GrackleParsers {

  def bigDecimalValue(v: Value): Option[BigDecimal] =
    v match {
      case IntValue(r)    => BigDecimal(r).some
      case FloatValue(r)  => BigDecimal(r).some
      case StringValue(r) => Either.catchNonFatal(BigDecimal(r)).toOption
      case _              => none
    }

  def longValue(v: Value): Option[Long] =
    v match {
      case IntValue(r)    => r.toLong.some
      case FloatValue(r)  => Either.catchNonFatal(r.toLong).toOption
      case StringValue(r) => Either.catchNonFatal(r.toLong).toOption
      case _              => none
    }

  def parseWavelength(units: List[(String, Value)]): Option[Wavelength] =
    units.find(_._2 != Value.AbsentValue) match {
      case Some(("picometers", IntValue(n))) =>
        Wavelength.intPicometers.getOption(n)
      case Some(("angstroms", n))            =>
        bigDecimalValue(n).flatMap(Wavelength.decimalAngstroms.getOption)
      case Some(("nanometers", n))           =>
        bigDecimalValue(n).flatMap(Wavelength.decimalNanometers.getOption)
      case Some(("micrometers", n))          =>
        bigDecimalValue(n).flatMap(Wavelength.decimalMicrometers.getOption)
      case _                                 => None
    }

  def parseRadialVelocity(units: List[(String, Value)]): Option[RadialVelocity] =
    units.find(_._2 != Value.AbsentValue) match {
      case Some(("centimetersPerSecond", IntValue(n))) =>
        RadialVelocity(n.withUnit[CentimetersPerSecond].toValue[BigDecimal].toUnit[MetersPerSecond])
      case Some(("metersPerSecond", n))                =>
        bigDecimalValue(n).flatMap(v => RadialVelocity(v.withUnit[MetersPerSecond]))
      case Some(("kilometersPerSecond", n))            =>
        bigDecimalValue(n).flatMap(v => RadialVelocity.kilometerspersecond.getOption(v))
      case _                                           => None
    }

  def parseRightAscension(units: List[(String, Value)]): Option[RightAscension] =
    units.find(_._2 != Value.AbsentValue) match {
      case Some(("microarcseconds", n))  =>
        longValue(n).map(Angle.fromMicroarcseconds).flatMap(RightAscension.fromAngleExact.getOption)
      case Some(("microseconds", n))     =>
        longValue(n).map(HourAngle.fromMicroseconds).flatMap(RightAscension.fromHourAngle.getOption)
      case Some(("degrees", n))          =>
        bigDecimalValue(n)
          .map(Angle.fromBigDecimalDegrees)
          .flatMap(RightAscension.fromAngleExact.getOption)
      case Some(("hours", n))            =>
        bigDecimalValue(n)
          .map(x => HourAngle.fromDoubleHours(x.toDouble))
          .flatMap(RightAscension.fromHourAngle.getOption)
      case Some(("hms", StringValue(s))) =>
        HourAngle.fromStringHMS.getOption(s).flatMap(RightAscension.fromHourAngle.getOption)
      case _                             => None
    }

  def parseDeclination(units: List[(String, Value)]): Option[Declination] =
    units.find(_._2 != Value.AbsentValue) match {
      case Some(("microarcseconds", n))  =>
        longValue(n).map(Angle.fromMicroarcseconds).flatMap(Declination.fromAngle.getOption)
      case Some(("degrees", n))          =>
        bigDecimalValue(n).map(Angle.fromBigDecimalDegrees).flatMap(Declination.fromAngle.getOption)
      case Some(("dms", StringValue(s))) => Declination.fromStringSignedDMS.getOption(s)
      case _                             => None
    }

  def parseEpoch(str: String): Option[Epoch] = Epoch.fromString.getOption(str)

  def parseRightAscensionVelocity(units: List[(String, Value)]): Option[ProperMotion.RA] =
    units.find(_._2 != Value.AbsentValue) match {
      case Some(("microarcsecondsPerYear", n)) =>
        longValue(n).flatMap(ProperMotion.RA.microarcsecondsPerYear.getOption)
      case Some(("milliarcsecondsPerYear", n)) =>
        bigDecimalValue(n).map(ProperMotion.RA.milliarcsecondsPerYear.reverseGet)
      case _                                   => None
    }

  def parseDeclinationVelocity(units: List[(String, Value)]): Option[ProperMotion.Dec] =
    units.find(_._2 != Value.AbsentValue) match {
      case Some(("microarcsecondsPerYear", n)) =>
        longValue(n).flatMap(ProperMotion.Dec.microarcsecondsPerYear.getOption)
      case Some(("milliarcsecondsPerYear", n)) =>
        bigDecimalValue(n).map(ProperMotion.Dec.milliarcsecondsPerYear.reverseGet)
      case _                                   => None
    }

  def parseProperMotion(l: List[(String, Value)]): Option[ProperMotion] = for {
    dra  <- l.collectFirst { case ("ra", ObjectValue(dral)) =>
              parseRightAscensionVelocity(dral)
            }.flatten
    ddec <- l.collectFirst { case ("dec", ObjectValue(ddecl)) =>
              parseDeclinationVelocity(ddecl)
            }.flatten
  } yield ProperMotion(dra, ddec)

  def parseParallax(units: List[(String, Value)]): Option[Parallax] =
    units.find(_._2 != Value.AbsentValue) match {
      case Some(("microarcseconds", n)) =>
        longValue(n).map(Parallax.fromMicroarcseconds)
      case Some(("milliarcseconds", n)) =>
        bigDecimalValue(n).map(Parallax.milliarcseconds.reverseGet)
      case _                            => None
    }

  def parseDistance(units: List[(String, Value)]): Option[Distance] =
    units.find(_._2 != Value.AbsentValue) match {
      case Some(("micrometers", n)) =>
        longValue(n).map(Distance.fromLongMicrometers)
      case Some(("millimeters", n)) =>
        bigDecimalValue(n).map(Distance.fromBigDecimalMillimeter)
      case _                        => None
    }

  def parseAngle(units: List[(String, Value)]): Option[Angle] =
    units.find(_._2 != Value.AbsentValue) match {
      case Some(("microarcseconds", n))  => longValue(n).map(Angle.fromMicroarcseconds)
      case Some(("microseconds", n))     =>
        bigDecimalValue(n).map(x => Angle.microarcseconds.reverseGet(x.toLong))
      case Some(("milliarcseconds", n))  =>
        bigDecimalValue(n).map(Angle.decimalMilliarcseconds.reverseGet)
      case Some(("milliseconds", n))     =>
        bigDecimalValue(n).map(Angle.decimalMilliarcseconds.reverseGet)
      case Some(("arcseconds", n))       => bigDecimalValue(n).map(Angle.fromBigDecimalArcseconds)
      case Some(("seconds", n))          => bigDecimalValue(n).map(Angle.fromBigDecimalArcseconds)
      case Some(("arcminutes", n))       =>
        bigDecimalValue(n).map(x => Angle.arcminutes.reverseGet(x.toInt))
      case Some(("minutes", n))          => bigDecimalValue(n).map(x => Angle.arcminutes.reverseGet(x.toInt))
      case Some(("degrees", n))          => bigDecimalValue(n).map(Angle.fromBigDecimalDegrees)
      case Some(("hours", n))            => bigDecimalValue(n).map(x => HourAngle.hours.reverseGet(x.toInt))
      case Some(("hms", StringValue(n))) => HourAngle.fromStringHMS.getOption(n)
      case Some(("dms", StringValue(n))) => Angle.fromStringDMS.getOption(n)
      case _                             => None
    }

  def parseEnumerated[T: Enumerated](s: String): Option[T] =
    Enumerated[T].all.find(e => Enumerated[T].tag(e).toScreamingSnakeCase === s)

  def parseTimeSpan(units: List[(String, Value)]): Option[TimeSpan] =
    units.find(_._2 != Value.AbsentValue) match {
      case Some(("microseconds", n))     => longValue(n).flatMap(TimeSpan.fromMicroseconds)
      case Some(("milliseconds", n))     => bigDecimalValue(n).flatMap(TimeSpan.fromMilliseconds)
      case Some(("seconds", n))          => bigDecimalValue(n).flatMap(TimeSpan.fromSeconds)
      case Some(("minutes", n))          => bigDecimalValue(n).flatMap(TimeSpan.fromMinutes)
      case Some(("hours", n))            => bigDecimalValue(n).flatMap(TimeSpan.fromHours)
      case Some(("iso", StringValue(n))) => TimeSpan.parse(n).toOption
      case _                             => None
    }

}
