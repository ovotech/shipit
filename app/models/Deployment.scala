package models

import java.time.OffsetDateTime

case class Link(title: String, url: String)

sealed trait DeploymentResult

object DeploymentResult {
  case object Succeeded extends DeploymentResult
  case object Failed    extends DeploymentResult
  case object Cancelled extends DeploymentResult

  def fromLowerCaseString(string: String) = string match {
    case "succeeded" => Some(Succeeded)
    case "failed"    => Some(Failed)
    case "cancelled" => Some(Cancelled)
    case _           => None
  }

  def toLowerCaseString(result: DeploymentResult) = result.toString.toLowerCase

}

case class Deployment(
    team: String,
    service: String,
    jiraComponent: Option[String],
    buildId: String,
    timestamp: OffsetDateTime,
    links: Seq[Link],
    note: Option[String],
    result: DeploymentResult
)
