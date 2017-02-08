package kafka

import java.nio.charset.StandardCharsets
import java.util

import models.DeploymentKafkaEvent
import org.apache.kafka.common.serialization.Deserializer
import play.api.Logger

object Serialization {

  val deploymentKafkaEventDeserializer = new Deserializer[Option[DeploymentKafkaEvent]] {
    def configure(configs: util.Map[String, _], isKey: Boolean) = {}
    def close() = {}
    def deserialize(topic: String, data: Array[Byte]) = {
      import io.circe.parser._
      import io.circe.generic.auto._
      import es.CirceDecoders.decodeDeploymentResult
      import cats.syntax.either._

      val either = for {
        json <- parse(new String(data, StandardCharsets.UTF_8))
        event <- json.as[DeploymentKafkaEvent]
      } yield event

      either
        .leftMap(err => {
          Logger.warn(s"Ignoring invalid Kafka event", err)
          err
        })
        .toOption

    }
  }

}
