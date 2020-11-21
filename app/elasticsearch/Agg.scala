package elasticsearch

import cats.syntax.apply._
import io.circe.Decoder
import io.circe.Decoder.decodeList

sealed trait Agg

/**
  * ElasticSearch lets you aggregate data into buckets or as metrics (like max)
  * here we define some decoders to chain together for nested aggregations + sub aggregations
  */
object Agg {

  case class Bucket[A](key: String, subAgg: A) extends Agg
  case class Max[A](value: A)                  extends Agg

  private def decodeSingleAgg[A: Decoder]: Decoder[Bucket[A]] =
    Decoder(c => (c.get[String]("key"), c.as[A]).mapN(Bucket.apply))

  def bucket[A: Decoder](name: String): Decoder[List[Bucket[A]]] =
    Decoder(cursor => cursor.downField(name).downField("buckets").as(decodeList(decodeSingleAgg[A])))

  def max[A: Decoder](name: String): Decoder[Max[A]] =
    Decoder(c => c.downField(name).get[A]("value_as_string")).map(Max.apply)
}
