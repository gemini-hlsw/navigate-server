// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import ch.qos.logback.classic.spi.ILoggingEvent
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.util.Timestamp
import lucuma.odb.json.angle.query.given
import lucuma.odb.json.offset.query.given
import navigate.model.AcMechsState
import navigate.model.AcquisitionAdjustment
import navigate.model.FocalPlaneOffset
import navigate.model.HandsetAdjustment.HorizontalAdjustment
import navigate.model.NavigateState
import navigate.model.PointingCorrections
import navigate.model.PwfsMechsState
import navigate.model.ServerConfiguration
import navigate.server.tcs.GuideState
import navigate.server.tcs.GuidersQualityValues
import navigate.server.tcs.MechSystemState
import navigate.server.tcs.TargetOffsets
import navigate.server.tcs.TelescopeState

package object encoder {

  given Encoder[ILoggingEvent] = e =>
    Json.obj(
      "timestamp" -> Timestamp.fromInstantTruncated(e.getInstant).getOrElse(Timestamp.Min).asJson,
      "level"     -> e.getLevel.toString.asJson,
      "thread"    -> e.getThreadName.asJson,
      "message"   -> e.getFormattedMessage.asJson
    )

  given Encoder[GuideState] = s => {
    val m2fields: List[(String, Json)] = s.m2Guide match {
      case M2GuideConfig.M2GuideOff               =>
        List(
          "m2Inputs" -> Json.Null,
          "m2Coma"   -> Json.Null
        )
      case M2GuideConfig.M2GuideOn(coma, sources) =>
        List(
          "m2Inputs" -> sources.asJson,
          "m2Coma"   -> coma.asJson
        )
    }
    val m1field: List[(String, Json)]  = s.m1Guide match {
      case M1GuideConfig.M1GuideOff        => List("m1Input" -> Json.Null)
      case M1GuideConfig.M1GuideOn(source) => List("m1Input" -> source.asJson)
    }

    Json.fromFields(
      m2fields ++ m1field ++ List(
        "mountOffload"  -> s.mountOffload.asJson,
        "p1Integrating" -> s.p1Integrating.asJson,
        "p2Integrating" -> s.p2Integrating.asJson,
        "oiIntegrating" -> s.oiIntegrating.asJson,
        "acIntegrating" -> s.acIntegrating.asJson
      )
    )
  }

  given Encoder[GuidersQualityValues.GuiderQuality] = s =>
    Json.obj(
      "flux"             -> s.flux.asJson,
      "centroidDetected" -> s.centroidDetected.asJson
    )

  given Encoder[GuidersQualityValues] = s =>
    Json.obj(
      "pwfs1" -> s.pwfs1.asJson,
      "pwfs2" -> s.pwfs2.asJson,
      "oiwfs" -> s.oiwfs.asJson
    )

  given Encoder[MechSystemState] = s =>
    Json.obj(
      "parked" -> s.parked.asJson,
      "follow" -> s.following.asJson
    )

  given Encoder[TelescopeState] = s =>
    Json.obj(
      "mount" -> s.mount.asJson,
      "scs"   -> s.scs.asJson,
      "crcs"  -> s.crcs.asJson,
      "pwfs1" -> s.pwfs1.asJson,
      "pwfs2" -> s.pwfs2.asJson,
      "oiwfs" -> s.oiwfs.asJson
    )

  given Encoder[AcquisitionAdjustment] = s =>
    Json.obj(
      "offset"  -> s.offset.asJson,
      "ipa"     -> s.ipa.asJson,
      "iaa"     -> s.iaa.asJson,
      "command" -> s.command.asJson
    )

  given Encoder[NavigateState] = s =>
    Json.obj(
      "onSwappedTarget" -> s.onSwappedTarget.asJson
    )

  given Encoder[FocalPlaneOffset] = s =>
    Json.obj(
      "deltaX" -> s.deltaX.asJson,
      "deltaY" -> s.deltaY.asJson
    )

  given Encoder[TargetOffsets] = s =>
    Json.obj(
      "sourceA" -> s.sourceA.asJson,
      "pwfs1"   -> s.pwfs1.asJson,
      "pwfs2"   -> s.pwfs2.asJson,
      "oiwfs"   -> s.oiwfs.asJson
    )

  given Encoder[HorizontalAdjustment] = s =>
    Json.obj(
      "azimuth"   -> s.deltaAz.asJson,
      "elevation" -> s.deltaEl.asJson
    )

  given Encoder[PointingCorrections] = s =>
    Json.obj(
      "local" -> s.local.asJson,
      "guide" -> s.guide.asJson
    )

  given Encoder[ServerConfiguration] = s =>
    Json.obj(
      "version" -> s.version.asJson,
      "site"    -> s.site.asJson,
      "odbUri"  -> s.odbUri.asJson,
      "ssoUri"  -> s.ssoUri.asJson
    )

  given Encoder[AcMechsState] = s =>
    Json.obj(
      "lens"     -> s.lens.asJson,
      "filter"   -> s.filter.asJson,
      "ndFilter" -> s.ndFilter.asJson
    )

  given Encoder[PwfsMechsState] = s =>
    Json.obj(
      "filter"    -> s.filter.asJson,
      "fieldStop" -> s.fieldStop.asJson
    )

}
