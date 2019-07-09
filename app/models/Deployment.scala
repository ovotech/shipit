package models

import java.time.OffsetDateTime

case class Link(title: String, url: String)

case class Deployment(
    team: String,
    service: String,
    buildId: String,
    timestamp: OffsetDateTime,
    links: Seq[Link],
    note: Option[String]
)
