package views

import java.time.{Instant, OffsetDateTime, ZoneId, ZoneOffset}
import java.time.format.DateTimeFormatter

object ViewHelper {

  def formatDate(offsetDateTime: OffsetDateTime): String =
    offsetDateTime.atZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " GMT"

  def formatOptDate(opt: Option[OffsetDateTime]): String =
    opt.map(formatDate).getOrElse("(unknown)")

  def dateToLong(offsetDateTime: OffsetDateTime): Long = {
    offsetDateTime.toInstant.toEpochMilli
  }

}
