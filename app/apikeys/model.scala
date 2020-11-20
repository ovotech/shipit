package apikeys

import java.time.{Instant, OffsetDateTime, ZoneId}
import java.util.concurrent.TimeUnit.MILLISECONDS

import cats.Functor
import cats.effect.Clock
import cats.syntax.functor._

case class NewApiKey(
    key: String,
    description: Option[String],
    createdBy: String
)

case class ExistingApiKey(
    key: String,
    description: Option[String],
    createdAt: OffsetDateTime,
    createdBy: String,
    active: Boolean,
    lastUsed: Option[OffsetDateTime]
)

object ExistingApiKey {

  def fromNew[F[_]: Clock: Functor](newApiKey: NewApiKey): F[ExistingApiKey] =
    Clock[F].realTime(MILLISECONDS).map(Instant.ofEpochMilli).map { time =>
      ExistingApiKey(
        newApiKey.key,
        description = newApiKey.description,
        createdAt = OffsetDateTime.ofInstant(time, ZoneId.of("UTC")),
        createdBy = newApiKey.createdBy,
        lastUsed = None,
        active = true
      )
    }
}
