package elasticsearch

import java.time.OffsetDateTime

import io.circe.{Decoder, Encoder}

import scala.util.Try

object CirceCodecs {

  implicit val decodeOffsetDateTime: Decoder[OffsetDateTime] =
    Decoder.decodeString.emapTry(x => Try(OffsetDateTime.parse(x)))

  implicit val encodeOffsetDateTime: Encoder[OffsetDateTime] =
    Encoder.encodeString.contramap(_.toString) // maybe
}
