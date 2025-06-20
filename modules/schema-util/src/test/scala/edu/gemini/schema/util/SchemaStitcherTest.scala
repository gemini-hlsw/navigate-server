// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.schema.util

import cats.effect.IO
import grackle.Result.Success
import grackle.{Result, Schema}
import munit.CatsEffectSuite

import java.nio.file.Path

class SchemaStitcherTest extends CatsEffectSuite {
  import SchemaStitcherTest.*

  test("SchemaStitcher should parse import statements") {
    schemaResolver.resolve(Path.of("baseSchema.graphql")).map { x =>
      val a = x.getLines.toList.map(SchemaStitcher.importLineParser.parse).collect {
        case Right((_, (els, path))) => (els, path)
      }

      assertEquals(a.length, 2)
      a(0) match {
        case (SchemaStitcher.AllElements, path) => assertEquals(path, Path.of("schema1.graphql"))
        case _                                  => fail
      }
      a(1) match {
        case (SchemaStitcher.ElementList(List("TypeA")), path) =>
          assertEquals(path, Path.of("schema2.graphql"))
        case _                                                 => fail
      }
    }
  }

  test("SchemaStitcher should compose schema") {
    SchemaStitcher[IO](Path.of("baseSchema.graphql"), schemaResolver).build.map { x =>
      (x, expectedSchema) match {
        case (Success(a), Success(b)) => assertEquals(a.toString, b.toString)
        case _                        => fail("Error creating schema")
      }
    }
  }

}

object SchemaStitcherTest {

  val schema1: String = """
    |#import TypeA from "schema2.graphql"
    |
    |type TypeB {
    |  val1: TypeA!
    |}
  """.stripMargin

  val schema2: String = """
    |enum EnumX {
    |  VAL0
    |  VAL1
    |}
    |
    |type TypeA {
    |  attr0: [EnumX]!
    |}
    |
    |type TypeB {
    |  attr0: Boolean!
    |  attr1: Float
    |}
    |
    |type TypeX {
    |  attr0: [TypeA]!
    |}
  """.stripMargin

  val baseSchema: String = """
    |#import * from "schema1.graphql"
    |#import TypeA, TypeX from "schema2.graphql"
    |
    |type TypeC {
    |  attr0: TypeB
    |}
    |
    |type Query {
    |  query1(par: TypeA!): TypeC!
    |  query2(par: TypeX!): TypeC!
    |}
  """.stripMargin

  val expectedSchema: Result[Schema] = Schema("""
    |enum EnumX {
    |  VAL0
    |  VAL1
    |}
    |
    |type TypeA {
    |  attr0: [EnumX]!
    |}
    |
    |type TypeX {
    |  attr0: [TypeA]!
    |}
    |
    |type TypeB {
    |  val1: TypeA!
    |}
    |
    |type TypeC {
    |  attr0: TypeB
    |}
    |
    |type Query {
    |  query1(par: TypeA!): TypeC!
    |  query2(par: TypeX!): TypeC!
    |}
    |""".stripMargin)

  val schemaResolver: SourceResolver[IO] = SourceResolver.fromStringMap(
    Map(
      Path.of("baseSchema.graphql") -> baseSchema,
      Path.of("schema1.graphql")    -> schema1,
      Path.of("schema2.graphql")    -> schema2
    )
  )

}
