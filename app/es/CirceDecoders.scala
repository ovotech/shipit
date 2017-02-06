package es

import java.time.OffsetDateTime

import io.circe.Decoder
import models.DeploymentResult
import models.DeploymentResult.{Cancelled, Failed, Succeeded}

import scala.util.Try

object CirceDecoders {

  implicit val decodeOffsetDateTime: Decoder[OffsetDateTime] =
    Decoder.decodeString.emapTry(x => Try(OffsetDateTime.parse(x)))

  implicit val decodeDeploymentResult: Decoder[DeploymentResult] = Decoder.decodeString.emap {
    case "Succeeded" => Right(Succeeded)
    case "Failed" => Right(Failed)
    case "Cancelled" => Right(Cancelled)
    case other => Left(s"Unrecognised deployment result: $other")
  }

}
