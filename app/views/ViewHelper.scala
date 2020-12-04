package views

import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneOffset}

import _root_.apikeys.ExistingApiKey

object ViewHelper {

  def formatDate(offsetDateTime: OffsetDateTime): String =
    offsetDateTime
      .atZoneSameInstant(ZoneOffset.UTC)
      .format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))

  def statusText(apiKey: ExistingApiKey): String =
    if (apiKey.active) "Active" else "Disabled"

  def buttonClass(apiKey: ExistingApiKey): String =
    if (apiKey.active) "success" else "warning"

}
