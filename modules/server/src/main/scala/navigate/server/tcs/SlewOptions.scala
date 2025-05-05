// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.util.NewType

object ZeroChopThrow            extends NewType[Boolean]
object ZeroSourceOffset         extends NewType[Boolean]
object ZeroSourceDiffTrack      extends NewType[Boolean]
object ZeroMountOffset          extends NewType[Boolean]
object ZeroMountDiffTrack       extends NewType[Boolean]
object ShortcircuitTargetFilter extends NewType[Boolean]
object ShortcircuitMountFilter  extends NewType[Boolean]
object ResetPointing            extends NewType[Boolean]
object StopGuide                extends NewType[Boolean]
object ZeroGuideOffset          extends NewType[Boolean]
object ZeroInstrumentOffset     extends NewType[Boolean]
object AutoparkPwfs1            extends NewType[Boolean]
object AutoparkPwfs2            extends NewType[Boolean]
object AutoparkOiwfs            extends NewType[Boolean]
object AutoparkGems             extends NewType[Boolean]
object AutoparkAowfs            extends NewType[Boolean]

case class SlewOptions(
  zeroChopThrow:            ZeroChopThrow.Type,
  zeroSourceOffset:         ZeroSourceOffset.Type,
  zeroSourceDiffTrack:      ZeroSourceDiffTrack.Type,
  zeroMountOffset:          ZeroMountOffset.Type,
  zeroMountDiffTrack:       ZeroMountDiffTrack.Type,
  shortcircuitTargetFilter: ShortcircuitTargetFilter.Type,
  shortcircuitMountFilter:  ShortcircuitMountFilter.Type,
  resetPointing:            ResetPointing.Type,
  stopGuide:                StopGuide.Type,
  zeroGuideOffset:          ZeroGuideOffset.Type,
  zeroInstrumentOffset:     ZeroInstrumentOffset.Type,
  autoparkPwfs1:            AutoparkPwfs1.Type,
  autoparkPwfs2:            AutoparkPwfs2.Type,
  autoparkOiwfs:            AutoparkOiwfs.Type,
  autoparkGems:             AutoparkGems.Type,
  autoparkAowfs:            AutoparkAowfs.Type
)

object SlewOptions {
  val default: SlewOptions = SlewOptions(
    ZeroChopThrow(false),
    ZeroSourceOffset(false),
    ZeroSourceDiffTrack(false),
    ZeroMountOffset(false),
    ZeroMountDiffTrack(false),
    ShortcircuitTargetFilter(false),
    ShortcircuitMountFilter(false),
    ResetPointing(false),
    StopGuide(false),
    ZeroGuideOffset(false),
    ZeroInstrumentOffset(false),
    AutoparkPwfs1(false),
    AutoparkPwfs2(false),
    AutoparkOiwfs(false),
    AutoparkGems(false),
    AutoparkAowfs(false)
  )
}
