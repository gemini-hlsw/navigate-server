// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate

import cats.syntax.foldable.*
import cats.syntax.option.*
import lucuma.core.util.Enumerated

import java.lang.Boolean as JBoolean
import java.lang.Double as JDouble
import java.lang.Float as JFloat
import java.lang.Integer as JInteger

package epics {

  sealed trait Convert[T, J] {
    def toJava(v:   T): Option[J]
    def fromJava(x: J): Option[T]
  }

  sealed trait ToJavaType[T] {
    type javaType
    val clazz: Class[javaType]
    val convert: Convert[T, javaType]
  }

}

package object epics {

  given ToJavaType[Boolean] = new ToJavaType[Boolean] {
    override type javaType = JBoolean
    override val clazz: Class[JBoolean]              = classOf[JBoolean]
    override val convert: Convert[Boolean, JBoolean] = new Convert[Boolean, JBoolean] {
      override def toJava(v:   Boolean): Option[JBoolean] = JBoolean.valueOf(v).some
      override def fromJava(x: JBoolean): Option[Boolean] = x.booleanValue().some
    }
  }

  given ToJavaType[Int] = new ToJavaType[Int] {
    override type javaType = JInteger
    override val clazz: Class[JInteger]          = classOf[JInteger]
    override val convert: Convert[Int, JInteger] = new Convert[Int, JInteger] {
      override def toJava(v:   Int): Option[JInteger] = JInteger.valueOf(v).some
      override def fromJava(x: JInteger): Option[Int] = x.toInt.some
    }
  }

  given ToJavaType[Double] = new ToJavaType[Double] {
    override type javaType = JDouble
    override val clazz: Class[javaType]            = classOf[JDouble]
    override val convert: Convert[Double, JDouble] = new Convert[Double, JDouble] {
      override def toJava(v:   Double): Option[JDouble] = JDouble.valueOf(v).some
      override def fromJava(x: JDouble): Option[Double] = x.toDouble.some
    }
  }

  given ToJavaType[Float] = new ToJavaType[Float] {
    override type javaType = JFloat
    override val clazz: Class[javaType]          = classOf[JFloat]
    override val convert: Convert[Float, JFloat] = new Convert[Float, JFloat] {
      override def toJava(v:   Float): Option[JFloat] = JFloat.valueOf(v).some
      override def fromJava(x: JFloat): Option[Float] = x.toFloat.some
    }
  }

  given ToJavaType[String] = new ToJavaType[String] {
    override type javaType = String
    override val clazz: Class[javaType]           = classOf[String]
    override val convert: Convert[String, String] = new Convert[String, String] {
      override def toJava(v:   String): Option[String] = v.some
      override def fromJava(x: String): Option[String] = x.some
    }
  }

  given [T: Enumerated]: ToJavaType[T] = new ToJavaType[T] {
    override type javaType = JInteger
    override val clazz: Class[javaType]        = classOf[JInteger]
    override val convert: Convert[T, JInteger] = new Convert[T, JInteger] {
      override def toJava(v: T): Option[JInteger] =
        JInteger.valueOf(Enumerated[T].all.indexOf(v)).some

      override def fromJava(x: JInteger): Option[T] = Enumerated[T].all.get(x.toLong)
    }
  }

}
