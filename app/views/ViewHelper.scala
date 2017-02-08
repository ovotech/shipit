package views

import java.time.{Instant, OffsetDateTime, ZoneId, ZoneOffset}
import java.time.format.DateTimeFormatter

import models.DeploymentResult.{Cancelled, Failed, Succeeded}
import models.{ApiKey, Deployment}

object ViewHelper {

  def formatDate(offsetDateTime: OffsetDateTime): String =
    offsetDateTime.atZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " GMT"

  def formatOptDate(opt: Option[OffsetDateTime]): String =
    opt.map(formatDate).getOrElse("(unknown)")

  def dateToLong(offsetDateTime: OffsetDateTime): Long = {
    offsetDateTime.toInstant.toEpochMilli
  }

  def statusBadge(apiKey: ApiKey) =
    if (apiKey.active)
      <span class="label label-primary">Active</span>
    else
      <span class="label label-default">Disabled</span>

  def resultBadge(deployment: Deployment) = deployment.result match {
    case Succeeded => <span class="label label-primary">Succeeded</span>
    case Failed => <span class="label label-danger">Failed</span>
    case Cancelled => <span class="label label-warning">Cancelled</span>
  }
}
