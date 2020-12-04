package views

import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneOffset}
import _root_.apikeys.ExistingApiKey
import _root_.deployments.{Environment, SearchTerms}
import play.twirl.api.Html

object ViewHelper {

  def formatDate(offsetDateTime: OffsetDateTime): String =
    offsetDateTime
      .atZoneSameInstant(ZoneOffset.UTC)
      .format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))

  def statusText(apiKey: ExistingApiKey): String =
    if (apiKey.active) "Active" else "Disabled"

  def buttonClass(apiKey: ExistingApiKey): String =
    if (apiKey.active) "success" else "warning"

  def envSelected(terms: SearchTerms)(environment: Option[Environment]): Html =
    if (terms.environment == environment) Html("selected") else Html("")

  def envBadgeClass(environment: Environment): String =
    if (environment == Environment.Prod) "success" else "warning"
}
