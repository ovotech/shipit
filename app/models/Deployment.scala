package models

import java.time.OffsetDateTime

case class Link(title: String, url: String)

sealed trait DeploymentResult

object DeploymentStatus {
  case object Succeeded extends DeploymentResult
  case object Failed extends DeploymentResult
  case object Cancelled extends DeploymentResult
}

case class Deployment(
                   id: String,
                   team: String,
                   service: String,
                   buildId: String,
                   timestamp: OffsetDateTime,
                   links: Seq[Link],
                   result: DeploymentResult
                 )

