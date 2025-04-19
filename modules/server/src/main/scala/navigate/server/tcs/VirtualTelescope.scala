// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.util.Enumerated

enum VirtualTelescope(val tag: String) derives Enumerated {
  case Mount extends VirtualTelescope("Mount")

  case SourceA extends VirtualTelescope("SourceA")

  case SourceB extends VirtualTelescope("SourceB")

  case SourceC extends VirtualTelescope("SourceC")

  case Pwfs1 extends VirtualTelescope("Pwfs1")

  case Pwfs2 extends VirtualTelescope("Pwfs2")

  case Oiwfs extends VirtualTelescope("Oiwfs")

  case G1 extends VirtualTelescope("G1")

  case G2 extends VirtualTelescope("G2")

  case G3 extends VirtualTelescope("G3")

  case G4 extends VirtualTelescope("G4")

}
