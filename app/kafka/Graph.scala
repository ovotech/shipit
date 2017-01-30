package kafka

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.kafka.scaladsl.Consumer.Control
import play.api.Logger

object Graph {

  def build[V](kafkaHosts: String, groupId: String, topic: String, deserializer: Deserializer[Option[V]])
              (implicit actorSystem: ActorSystem): Source[V, Control] = {

    Logger.info(s"Building graph hosts=[$kafkaHosts] groupId=[$groupId] topic=[$topic]")

    val consumerSettings =
      ConsumerSettings(actorSystem, new StringDeserializer, deserializer)
        .withBootstrapServers(kafkaHosts)
        .withGroupId(groupId)
      .withMaxWakeups(50)

    Consumer.atMostOnceSource(consumerSettings, Subscriptions.topics(topic))
        .flatMapConcat { record =>
          Source[V](record.value().to[collection.immutable.Iterable])
        }
  }

}
