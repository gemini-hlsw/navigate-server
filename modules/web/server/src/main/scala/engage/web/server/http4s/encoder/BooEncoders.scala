// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.http4s.encoder

import cats.effect.Concurrent
import engage.model.boopickle.ModelBooPicklers
import engage.model.security.UserDetails
import engage.model.security.UserLoginRequest
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.booPickle.instances.BooPickleInstances

/**
 * Contains http4s implicit encoders of model objects
 */
trait BooEncoders extends ModelBooPicklers with BooPickleInstances {
  // Decoders, Included here instead of the on the object definitions to avoid
  // a circular dependency on http4s
  implicit def usrLoginDecoder[F[_]: Concurrent]: EntityDecoder[F, UserLoginRequest] =
    booOf[F, UserLoginRequest]
  implicit def userDetailEncoder[F[_]]: EntityEncoder[F, UserDetails]                =
    booEncoderOf[F, UserDetails]
}

/**
 * Contains http4s implicit encoders of model objects, from the point of view of a client
 */
trait ClientBooEncoders extends ModelBooPicklers with BooPickleInstances {
  implicit def usrLoginEncoder[F[_]]: EntityEncoder[F, UserLoginRequest] =
    booEncoderOf[F, UserLoginRequest]
}
