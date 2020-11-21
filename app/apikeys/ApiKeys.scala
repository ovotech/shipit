package apikeys

import java.time.ZoneOffset.UTC
import java.time.{Instant, OffsetDateTime}
import java.util.concurrent.TimeUnit.MILLISECONDS

import cats.effect.{Clock, Sync}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.tagless.FunctorK
import com.sksamuel.elastic4s.ElasticDsl.{createIndex, _}
import com.sksamuel.elastic4s.cats.effect.instances._
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.fields.{BooleanField, DateField, KeywordField, TextField}
import com.sksamuel.elastic4s.requests.indexes.CreateIndexRequest
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.{ElasticClient, Executor}
import elasticsearch.Pagination._
import elasticsearch.Instances._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import models.Identified

trait ApiKeys[F[_]] {
  def create(key: NewApiKey): F[Identified[ExistingApiKey]]
  def findByKey(key: String): F[Option[Identified[ExistingApiKey]]]
  def list(page: Int): F[Page[Identified[ExistingApiKey]]]
  def updateLastUsed(keyId: String): F[Unit]
  def disable(keyId: String): F[Unit]
  def enable(keyId: String): F[Unit]
  def delete(keyId: String): F[Boolean]
  def createIndex: F[Unit]
}

object ApiKeys {

  val indexName: String              = "shipit_v3_apikeys"
  implicit val fk: FunctorK[ApiKeys] = cats.tagless.Derive.functorK

  private def byKeyQuery(key: String): SearchRequest =
    search(indexName).query(termQuery("key", key)).size(1)

  private def listQuery(page: Int): SearchRequest =
    search(indexName).matchAllQuery().from(pageToOffset(page)).limit(PageSize).sortByFieldAsc("createdBy")

  private val createApiKeys: CreateIndexRequest =
    createIndex(indexName)
      .mapping(
        properties(
          KeywordField("key"),
          TextField("description"),
          DateField("createdAt"),
          KeywordField("createdBy"),
          BooleanField("active"),
          DateField("lastUsed")
        )
      )

  def apply[F[_]: Executor: Clock](client: ElasticClient)(implicit F: Sync[F]): ApiKeys[F] =
    new ApiKeys[F] {

      implicit val encoder: Encoder[ExistingApiKey] = deriveEncoder
      implicit val decoder: Decoder[ExistingApiKey] = deriveDecoder

      def createIndex: F[Unit] =
        client.execute(createApiKeys).void

      def create(key: NewApiKey): F[Identified[ExistingApiKey]] =
        for {
          existing <- ExistingApiKey.fromNew[F](key)
          response <- client.execute(indexInto(indexName).source(existing))
        } yield Identified(id = response.result.id, existing)

      def findByKey(key: String): F[Option[Identified[ExistingApiKey]]] =
        for {
          response <- client.execute(byKeyQuery(key))
          data     <- response.result.safeTo[Identified[ExistingApiKey]].toList.traverse(F.fromTry)
        } yield data.headOption

      def list(page: Int): F[Page[Identified[ExistingApiKey]]] =
        for {
          response <- client.execute(listQuery(page))
          data     <- response.result.safeTo[Identified[ExistingApiKey]].toList.traverse(F.fromTry)
        } yield Page(data, page, response.result.totalHits.toInt)

      def updateLastUsed(keyId: String): F[Unit] =
        Clock[F].realTime(MILLISECONDS).map(Instant.ofEpochMilli).flatMap { i =>
          val update = updateById(indexName, keyId).doc("lastUsed" -> OffsetDateTime.ofInstant(i, UTC))
          client.execute(update).void
        }

      def disable(keyId: String): F[Unit] =
        client.execute(updateById(indexName, keyId).doc("active" -> false)).void

      def enable(keyId: String): F[Unit] =
        client.execute(updateById(indexName, keyId).doc("active" -> true)).void

      def delete(keyId: String): F[Boolean] =
        client.execute(deleteById(indexName, keyId)).map(_.isSuccess)
    }
}
