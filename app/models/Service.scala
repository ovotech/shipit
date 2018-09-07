package models

import java.time.OffsetDateTime

case class Service(
    team: String,
    service: String,
    lastDeployed: OffsetDateTime
)
