// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Monad
import cats.Parallel
import cats.effect.Resource
import cats.effect.Temporal
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.math.Wavelength
import navigate.epics.EpicsService
import navigate.epics.VerifiedEpics.*
import navigate.model.enums
import navigate.model.enums.AcFilter
import navigate.model.enums.AcLens
import navigate.model.enums.AcNdFilter
import navigate.server.ApplyCommandResult
import navigate.server.acm.CadDirective
import navigate.server.acm.CarState
import navigate.server.acm.Decoder
import navigate.server.acm.GeminiApplyCommand
import navigate.server.acm.ParameterList.*
import navigate.server.acm.writeCadParam
import navigate.server.tcs.AcquisitionCameraEpicsSystem.AcquisitionCameraStatus

import scala.concurrent.duration.FiniteDuration

trait AcquisitionCameraEpicsSystem[F[_]] {
  val status: AcquisitionCameraStatus[F]

  def startCommand(
    timeout: FiniteDuration
  ): AcquisitionCameraEpicsSystem.AcquisitionCameraCommands[F]
}

object AcquisitionCameraEpicsSystem {

  val acLensDec: Decoder[String, AcLens] = {
    case "AC"    => AcLens.Ac
    case "HRWFS" => AcLens.Hrwfs
    case _       => AcLens.Ac
  }

  val acFilterDec: Decoder[String, AcFilter] = {
    case "neutral" => AcFilter.Neutral
    case "U-red1"  => AcFilter.U_Red1
    case "B-blue"  => AcFilter.B_Blue
    case "V-green" => AcFilter.V_Green
    case "R-red2"  => AcFilter.R_Red2
    case "I-red3"  => AcFilter.I_Red3
    case _         => AcFilter.Neutral
  }

  val acNdFilterDec: Decoder[String, AcNdFilter] = {
    case "ND3"    => AcNdFilter.Nd3
    case "ND2"    => AcNdFilter.Nd2
    case "ND1"    => AcNdFilter.Nd1
    case "nd100"  => AcNdFilter.Nd100
    case "nd1000" => AcNdFilter.Nd1000
    case "filt04" => AcNdFilter.Filt04
    case "filt06" => AcNdFilter.Filt06
    case "filt08" => AcNdFilter.Filt08
    case _        => AcNdFilter.Open
  }

  extension (flt: AcFilter) {
    def toWavelength: Wavelength = {
      val NanoToPico: Int = 1000
      flt match
        case AcFilter.Neutral => Wavelength.unsafeFromIntPicometers(500 * NanoToPico)
        case AcFilter.U_Red1  => Wavelength.unsafeFromIntPicometers(375 * NanoToPico)
        case AcFilter.B_Blue  => Wavelength.unsafeFromIntPicometers(425 * NanoToPico)
        case AcFilter.V_Green => Wavelength.unsafeFromIntPicometers(530 * NanoToPico)
        case AcFilter.R_Red2  => Wavelength.unsafeFromIntPicometers(640 * NanoToPico)
        case AcFilter.I_Red3  => Wavelength.unsafeFromIntPicometers(760 * NanoToPico)
    }
  }

  trait AcquisitionCameraStatus[F[_]] {
    def lens: VerifiedEpics[F, F, AcLens]
    def filter: VerifiedEpics[F, F, AcFilter]
    def ndFilter: VerifiedEpics[F, F, AcNdFilter]
    def observe: VerifiedEpics[F, F, CarState]
  }

  trait AcquisitionCameraCommands[F[_]] {
    def post: VerifiedEpics[F, F, ApplyCommandResult]
    def setLens(lens:            AcLens): AcquisitionCameraCommands[F]
    def setNdFilter(filter:      AcNdFilter): AcquisitionCameraCommands[F]
    def setColFilter(filter:     AcFilter): AcquisitionCameraCommands[F]
    def setExposureTime(expTime: Double): AcquisitionCameraCommands[F]
    def setNumberOfFrames(cnt:   Int): AcquisitionCameraCommands[F]
    def setOutput(opt:           Int): AcquisitionCameraCommands[F]
    def setDirectory(dir:        String): AcquisitionCameraCommands[F]
    def setFilename(file:        String): AcquisitionCameraCommands[F]
    def simFile(file:            String): AcquisitionCameraCommands[F]
    def setQuicklookStream(name: String): AcquisitionCameraCommands[F]
    def setDhsOption(opt:        Int): AcquisitionCameraCommands[F]
    def setBinning(bin:          Int): AcquisitionCameraCommands[F]
    def enableWindow(enable:     Int): AcquisitionCameraCommands[F]
    def setWindowX(v:            Int): AcquisitionCameraCommands[F]
    def setWindowY(v:            Int): AcquisitionCameraCommands[F]
    def setWindowWidth(v:        Int): AcquisitionCameraCommands[F]
    def setWindowHeight(v:       Int): AcquisitionCameraCommands[F]
    def setDhsLabel(label:       String): AcquisitionCameraCommands[F]
    def stop: AcquisitionCameraCommands[F]
  }

  case class AcquisitionCameraCommandsImpl[F[_]: {Monad, Parallel}](
    applyCmd: GeminiApplyCommand[F],
    chs:      AcquisitionCameraChannels[F],
    timeout:  FiniteDuration,
    params:   ParameterList[F]
  ) extends AcquisitionCameraCommands[F] {
    private def addParam(v: VerifiedEpics[F, F, Unit]): AcquisitionCameraCommands[F] =
      this.copy(params = params :+ v)

    override def post: VerifiedEpics[F, F, ApplyCommandResult] =
      params.compile *> applyCmd.post(timeout)

    override def setLens(lens: AcLens): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.lens)(lens)
    )

    override def setNdFilter(filter: AcNdFilter): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.ndFilter)(filter)
    )

    override def setColFilter(filter: AcFilter): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.filter)(filter)
    )

    override def setExposureTime(expTime: Double): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.expTime)(expTime)
    )

    override def setNumberOfFrames(cnt: Int): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.frameCount)(cnt)
    )

    override def setOutput(opt: Int): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.output)(opt)
    )

    override def setDirectory(dir: String): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.directory)(dir)
    )

    override def setFilename(file: String): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.fileName)(file)
    )

    override def simFile(file: String): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.simFile)(file)
    )

    override def setQuicklookStream(name: String): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.dhsStream)(name)
    )

    override def setDhsOption(opt: Int): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.dhsOption)(opt)
    )

    override def setBinning(bin: Int): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.binning)(bin)
    )

    override def enableWindow(enable: Int): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.windowing)(enable)
    )

    override def setWindowX(v: Int): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.centerX)(v)
    )

    override def setWindowY(v: Int): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.centerY)(v)
    )

    override def setWindowWidth(v: Int): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.width)(v)
    )

    override def setWindowHeight(v: Int): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.height)(v)
    )

    override def setDhsLabel(label: String): AcquisitionCameraCommands[F] = addParam(
      writeCadParam(chs.telltale, chs.dhsLabel)(label)
    )

    override def stop: AcquisitionCameraCommands[F] = addParam(
      writeChannel(chs.telltale, chs.stopDir)(CadDirective.MARK.pure[F])
    )
  }

  private[tcs] def buildSystem[F[_]: {Monad, Parallel}](
    applyCmd: GeminiApplyCommand[F],
    chs:      AcquisitionCameraChannels[F]
  ): AcquisitionCameraEpicsSystem[F] =
    new AcquisitionCameraEpicsSystem[F] {
      override val status: AcquisitionCameraStatus[F] = new AcquisitionCameraStatus[F] {
        override def lens: VerifiedEpics[F, F, AcLens] =
          readChannel(chs.telltale, chs.lensReadout).map(_.map(acLensDec.decode))

        override def filter: VerifiedEpics[F, F, AcFilter] =
          readChannel(chs.telltale, chs.filterReadout).map(_.map(acFilterDec.decode))

        override def observe: VerifiedEpics[F, F, CarState] =
          readChannel(chs.telltale, chs.observeInProgress)

        override def ndFilter: VerifiedEpics[F, F, AcNdFilter] =
          readChannel(chs.telltale, chs.ndFilterReadout).map(_.map(acNdFilterDec.decode))

      }

      override def startCommand(timeout: FiniteDuration): AcquisitionCameraCommands[F] =
        AcquisitionCameraCommandsImpl(applyCmd, chs, timeout, List.empty)
    }

  def build[F[_]: {Dispatcher, Temporal, Parallel}](
    service: EpicsService[F],
    top:     NonEmptyString
  ): Resource[F, AcquisitionCameraEpicsSystem[F]] =
    for {
      chs   <- AcquisitionCameraChannels.build(service, top)
      apply <- GeminiApplyCommand.build(service, chs.telltale, s"${top}apply", s"${top}applyC")
    } yield buildSystem(apply, chs)

}
