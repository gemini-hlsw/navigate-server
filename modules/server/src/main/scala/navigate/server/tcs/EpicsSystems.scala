// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

case class EpicsSystems[F[_]](
  tcsEpics: TcsEpicsSystem[F],
  pwfs1:    WfsEpicsSystem[F],
  pwfs2:    WfsEpicsSystem[F],
  oiwfs:    OiwfsEpicsSystem[F],
  mcs:      McsEpicsSystem[F],
  scs:      ScsEpicsSystem[F],
  crcs:     CrcsEpicsSystem[F],
  ags:      AgsEpicsSystem[F],
  hrwfs:    AcquisitionCameraEpicsSystem[F]
)
