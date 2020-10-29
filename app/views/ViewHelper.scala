package views

import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneOffset}

import _root_.apikeys.ExistingApiKey

object ViewHelper {

  def formatDate(offsetDateTime: OffsetDateTime): String =
    offsetDateTime
      .atZoneSameInstant(ZoneOffset.UTC)
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " GMT"

  def statusBadge(apiKey: ExistingApiKey) =
    if (apiKey.active)
      <span class="label label-primary">Active</span>
    else
      <span class="label label-default">Disabled</span>

}
