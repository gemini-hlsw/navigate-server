// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

case class InstrumentPorts(
  flamingos2Port: Int,
  ghostPort:      Int,
  gmosPort:       Int,
  gnirsPort:      Int,
  gpiPort:        Int,
  gsaoiPort:      Int,
  nifsPort:       Int,
  niriPort:       Int
)
