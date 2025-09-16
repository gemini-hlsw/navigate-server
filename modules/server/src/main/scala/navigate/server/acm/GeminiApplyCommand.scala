// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.acm

import cats.Applicative
import cats.Eq
import cats.Monad
import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.Temporal
import cats.effect.std.Dispatcher
import cats.effect.syntax.temporal.*
import cats.syntax.all.*
import fs2.RaiseThrowable.*
import fs2.Stream
import mouse.all.booleanSyntaxMouse
import navigate.epics.Channel
import navigate.epics.Channel.StreamEvent
import navigate.epics.EpicsService
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.VerifiedEpics
import navigate.epics.VerifiedEpics.*
import navigate.server.ApplyCommandResult

import scala.concurrent.duration.FiniteDuration

trait GeminiApplyCommand[F[_]] {

  /**
   * Given a pair of apply and CAR records, this function produces a program that will trigger the
   * apply record and then monitor the apply and CAR record to finally produce a command result. If
   * the command takes longer than the <code>timeout</code>, it will produce an error. The program
   * is contained in a <code>VerifiedEpics</code> that checks the connection to the channels.
   * @param timeout
   *   The timeout for running the command
   * @return
   */
  def post(timeout: FiniteDuration): VerifiedEpics[F, F, ApplyCommandResult]
}

object GeminiApplyCommand {

  sealed trait CmdPhase extends Product with Serializable

  case object WaitingBusy extends CmdPhase
  case object WaitingIdle extends CmdPhase

  sealed trait PostState                                    extends Product with Serializable
  case class Processing(aout: Option[Int], phase: CmdPhase) extends PostState

  sealed trait Event                   extends Product with Serializable
  case class ApplyValChange(v: Int)    extends Event
  case class CarValChange(v: CarState) extends Event

  final class GeminiApplyCommandImpl[F[_]: {Dispatcher, Temporal}](
    telltaleChannel: TelltaleChannel[F],
    apply:           ApplyRecord[F],
    car:             CarRecord[F]
  ) extends GeminiApplyCommand[F] {
    override def post(timeout: FiniteDuration): VerifiedEpics[F, F, ApplyCommandResult] = {
      val streamsV = for {
        avrs   <- eventStream(telltaleChannel, apply.oval)
        cvrs   <- eventStream(telltaleChannel, car.oval)
        dwr    <- writeChannel(telltaleChannel, apply.dir)(Applicative[F].pure(CadDirective.START))
                    .map(Resource.pure[F, F[Unit]])
        msrr   <- readChannel(telltaleChannel, apply.mess).map(Resource.pure[F, F[String]])
        omsrr  <- readChannel(telltaleChannel, car.omss).map(Resource.pure[F, F[String]])
        clidrr <- readChannel(telltaleChannel, car.clid).map(Resource.pure[F, F[Int]])
        avrr   <- readChannel(telltaleChannel, apply.oval).map(Resource.pure[F, F[Int]])
        cvrr   <- readChannel(telltaleChannel, car.oval).map(Resource.pure[F, F[CarState]])
      } yield for {
        avs   <- avrs
        cvs   <- cvrs
        dw    <- dwr
        msr   <- msrr
        omsr  <- omsrr
        clidr <- clidrr
        avr   <- avrr
        cvr   <- cvrr
      } yield (avs, cvs, dw, msr, omsr, clidr, avr, cvr)

      streamsV.map(_.use { case (avs, cvs, dw, msr, omsr, clidr, avr, cvr) =>
        processCommand(avs, cvs, dw, msr, omsr, clidr, avr, cvr).timeout(timeout)
      })

    }

    /**
     * processCommand is the heart of the acm apply-car processing. It implements a state machine
     * inside a Stream, that follows the algorithm described in Gemini ICD 1b, section 6.1. The
     * events of the state machine are changes on the EPICS channels apply.VAL and car.VAL. The
     * inputs are:
     *
     * @param avs:
     *   Stream of values from apply.VAL
     * @param cvs:
     *   Stream of values from car.VAL
     * @param dw:
     *   Effect that will write a START on apply.DIR when evaluated.
     * @param msr:
     *   Effect to read apply.MESS
     * @param omsr:
     *   Effect to read car.OMSS
     * @param clidr:
     *   Effect to read
     * @param car.CLID
     * @param avr:
     *   Effect to read apply.VAL
     * @param cvr:
     *   Effect to read car.VAL
     *
     * The monitors created by the CA library always give the current value as the first event (if
     * there is a current value). That holds true for the Streams created from those monitors. That
     * is the reason that apply.VAL is read at the beginning, to compare the initial value with the
     * stream values and recognize actual changes.
     */
    private def processCommand(
      avs:   Stream[F, StreamEvent[Int]],
      cvs:   Stream[F, StreamEvent[CarState]],
      dw:    F[Unit],
      msr:   F[String],
      omsr:  F[String],
      clidr: F[Int],
      avr:   F[Int],
      cvr:   F[CarState]
    ): F[ApplyCommandResult] = {
      val strm = for {
        av0 <- Stream.eval(avr.attempt.map(_.toOption))
        cv0 <- Stream.eval(cvr.attempt.map(_.toOption))
        _   <- Stream.eval(dw)
        r   <-
          removeRepeated(avs, av0, s"Apply record ${apply.name} disconnected", ApplyValChange.apply)
            .merge(
              removeRepeated(cvs, cv0, s"CAR record ${apply.name} disconnected", CarValChange.apply)
            )
      } yield r

      commandStateMachine(apply.name, car.name, msr, omsr, clidr, strm)
    }

    private def removeRepeated[U: Eq](
      strm:          Stream[F, StreamEvent[U]],
      v0:            Option[U],
      disconnectMsg: String,
      out:           U => Event
    ): Stream[F, Event] =
      strm
        .flatMap {
          case StreamEvent.ValueChanged(v) => Stream(v)
          case StreamEvent.Disconnected    => Stream.raiseError[F](new Throwable(disconnectMsg))
          case _                           => Stream.empty
        }
        .mapAccumulate(v0) { case (acc, v) => (v.some, acc.forall(_ =!= v).option(out(v))) }
        .map(_._2)
        .unNone

  }

  private[acm] def commandStateMachine[F[_]: Concurrent](
    applyName: String,
    carName:   String,
    msr:       F[String],
    omsr:      F[String],
    clidr:     F[Int],
    strm:      Stream[F, Event]
  ): F[ApplyCommandResult] =
    strm
      .mapAccumulate[PostState, F[Option[ApplyCommandResult]]](Processing(None, WaitingBusy)) {
        (s, ev) =>
          (s, ev) match {
            case (Processing(None, x), ApplyValChange(v)) if v < 0               =>
              (Processing(None, x),
               msr.flatMap(msg =>
                 new Throwable(s"Apply record ${applyName} failed with error: $msg").raiseError
               )
              )
            case (Processing(None, x), ApplyValChange(v)) if v === 0             =>
              (Processing(None, x),
               new Throwable(
                 s"Apply record ${applyName} triggered externally while processing."
               ).raiseError
              )
            case (Processing(None, x), ApplyValChange(v)) if v > 0               =>
              (Processing(v.some, x), none[ApplyCommandResult].pure[F])
            case (Processing(Some(v), x), CarValChange(CarState.ERROR))          =>
              (Processing(Some(v), x),
               clidr.flatMap(c =>
                 (c >= v).fold(
                   omsr.flatMap(msg =>
                     new Throwable(s"CAR record ${carName} has error: $msg").raiseError
                   ),
                   none[ApplyCommandResult].pure[F]
                 )
               )
              )
            case (Processing(v, WaitingBusy), CarValChange(CarState.BUSY))       =>
              (Processing(v, WaitingIdle), none[ApplyCommandResult].pure[F])
            case (Processing(None, WaitingIdle), CarValChange(CarState.IDLE))    =>
              (Processing(None, WaitingBusy), none[ApplyCommandResult].pure[F])
            case (Processing(Some(v), WaitingIdle), CarValChange(CarState.IDLE)) =>
              (Processing(Some(v), WaitingBusy),
               clidr.map(c => (c >= v).option(ApplyCommandResult.Completed))
              )
            case (Processing(Some(v), _), CarValChange(CarState.PAUSED))         =>
              (Processing(Some(v), WaitingBusy),
               clidr.map(c => (c >= v).option(ApplyCommandResult.Paused))
              )
            case (x, _)                                                          =>
              (x, none[ApplyCommandResult].pure[F])
          }
      }
      .evalMap(_._2)
      .unNone
      .take(1)
      .compile
      .lastOrError

  def build[F[_]: {Dispatcher, Temporal}](
    srv:             EpicsService[F],
    telltaleChannel: TelltaleChannel[F],
    applyName:       String,
    carName:         String
  ): Resource[F, GeminiApplyCommand[F]] = for {
    apply <- ApplyRecord.build(srv, applyName)
    car   <- CarRecord.build(srv, carName)
  } yield new GeminiApplyCommandImpl[F](telltaleChannel, apply, car)

  def smartSetParam[F[_]: Monad, A, B](
    tt:  TelltaleChannel[F],
    st:  Channel[F, A],
    pr:  Channel[F, B],
    cmp: (A, B) => Boolean
  )(value: B): VerifiedEpics[F, F, Unit] = {
    val statusRead = readChannel(tt, st)
    val paramWrite = writeChannel(tt, pr)(value.pure[F])

    statusRead.flatMap(fa => ifF(fa.map(cmp(_, value)))(VerifiedEpics.unit[F, F])(paramWrite))
  }
}
