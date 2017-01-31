package models

import java.time.OffsetDateTime

case class Link(title: String, url: String)

sealed trait DeploymentStatus

object DeploymentStatus {
  case object Running extends DeploymentStatus
  case object Succeeded extends DeploymentStatus
  case object Failed extends DeploymentStatus
  case object Cancelled extends DeploymentStatus
}

case class Deployment(
                   id: String,
                   category: String,
                   service: String,
                   buildId: String,
                   links: Seq[Link],
                   startedAt: Option[OffsetDateTime],
                   finishedAt: Option[OffsetDateTime],
                   status: DeploymentStatus
                 )


