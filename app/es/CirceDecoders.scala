package es

import java.time.OffsetDateTime

import io.circe.Decoder

import scala.util.Try

object CirceDecoders {

  implicit val decodeOffsetDateTime: Decoder[OffsetDateTime] =
    Decoder.decodeString.emapTry(x => Try(OffsetDateTime.parse(x)))

}
