// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.schema

import cats.Eq

import java.nio.file.Path

package object util {
  given pathEq: Eq[Path] = Eq.fromUniversalEquals
}
