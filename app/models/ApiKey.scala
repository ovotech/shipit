package models

import java.time.OffsetDateTime

case class ApiKey(
    id: String,
    key: String,
    description: Option[String],
    createdAt: OffsetDateTime,
    createdBy: String,
    active: Boolean,
    lastUsed: Option[OffsetDateTime]
)
