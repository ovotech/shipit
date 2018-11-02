package views

import java.time.{OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter

import models.ApiKey

object ViewHelper {

  def formatDate(offsetDateTime: OffsetDateTime): String =
    offsetDateTime
      .atZoneSameInstant(ZoneOffset.UTC)
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " GMT"

  def statusBadge(apiKey: ApiKey) =
    if (apiKey.active)
      <span class="label label-primary">Active</span>
    else
      <span class="label label-default">Disabled</span>

}
