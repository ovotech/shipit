package apikeys

import java.time.OffsetDateTime

import cats.Id
import cats.data.Reader
import cats.effect.{ContextShift, IO}
import cats.syntax.either._
import com.google.gson.JsonElement
import elasticsearch.Elastic55._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.parse
import io.searchbox.client.JestClient
import io.searchbox.core.search.sort.Sort
import io.searchbox.core.{Delete, Index, Search, Update}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

trait ApiKeys[F[_]] {
  def create(key: String, description: Option[String], createdBy: String): F[ApiKey]
  def findByKey(key: String): F[Option[ApiKey]]
  def list(page: Int): F[Page[ApiKey]]
  def updateLastUsed(keyId: String): F[Unit]
  def disable(keyId: String): F[Unit]
  def enable(keyId: String): F[Unit]
  def delete(keyId: String): F[Boolean]
}

object ApiKeys {

  def old(jest: JestClient)(implicit cs: ContextShift[IO]): ApiKeys[IO] =
    new ApiKeys[IO] {

      val logger: Logger =
        LoggerFactory.getLogger("shipit.apikeys.ApiKeys.old")

      implicit val decoder: Decoder[String => ApiKey] =
        deriveDecoder[String => ApiKey] // no idea how this magically fills in the id field

      def _create(key: String, description: Option[String], createdBy: String): IO[ApiKey] = {
        val createdAt = OffsetDateTime.now()
        val map = Map(
          "key"                            -> key,
          "createdBy"                      -> createdBy,
          "createdAt"                      -> createdAt.toString,
          "active"                         -> true
        ) ++ description.map("description" -> _)
        val action = new Index.Builder(map.asJava)
          .index(IndexName)
          .`type`(Types.ApiKey)
          .build()
        jest.execIO(action).map { r =>
          ApiKey(r.getId, key, description, createdAt, createdBy, active = true, lastUsed = None)
        }
      }

      private def _delete(keyId: String): Reader[JestClient, Boolean] =
        Reader[JestClient, Boolean] { jest =>
          val action = new Delete.Builder(keyId)
            .index(IndexName)
            .`type`(Types.ApiKey)
            .build()
          val result = jest.execute(action)
          if (!result.isSucceeded) {
            logger.warn(s"Failed to delete API key. Error: ${result.getErrorMessage}")
          }
          result.isSucceeded
        }

      def updateActiveFlag(keyId: String, active: Boolean): Reader[JestClient, Unit] =
        Reader[JestClient, Unit] { jest =>
          val update =
            s"""
               |{
               |   "doc" : {
               |      "active": $active
               |   }
               |}
         """.stripMargin
          val action = new Update.Builder(update)
            .index(IndexName)
            .`type`(Types.ApiKey)
            .id(keyId)
            .build()
          jest.execute(action)
        }

      def create(key: String, description: Option[String], createdBy: String): Id[ApiKey] =
        executeAndRefresh(_create(key, description, createdBy)).run(jest)

      def parseHit(jsonElement: JsonElement, id: String): Option[ApiKey] = {
        val either = for {
          json       <- parse(jsonElement.toString).right
          incomplete <- json.as[String => ApiKey].right
        } yield incomplete.apply(id)
        either
          .leftMap(e => logger.warn("Failed to decode API key returned by ES", e))
          .toOption
      }
      def findByKey(key: String): Option[ApiKey] = {
        val query =
          s"""{
             |  "size": 1,
             |  "query": { "term": { "key": "$key" } }
             |}""".stripMargin
        val action = new Search.Builder(query)
          .addIndex(IndexName)
          .addType(Types.ApiKey)
          .build()
        val result = jest.execute(action)
        result
          .getHits(classOf[JsonElement])
          .asScala
          .flatMap(hit => parseHit(hit.source, hit.id))
          .headOption
      }

      def list(page: Int): Id[Page[ApiKey]] = {
        val query =
          s"""{
             |  "from": ${pageToOffset(page)},
             |  "size": $PageSize,
             |  "query": { "match_all": {} }
             |}""".stripMargin
        val action = new Search.Builder(query)
          .addIndex(IndexName)
          .addType(Types.ApiKey)
          .addSort(new Sort("createdBy"))
          .build()
        val result = jest.execute(action)
        val items = result
          .getHits(classOf[JsonElement])
          .asScala
          .flatMap(hit => parseHit(hit.source, hit.id))
        Page(items, page, result.getTotal.toInt)
      }

      def updateLastUsed(keyId: String): Id[Unit] = {
        val update =
          s"""
             |{
             |   "doc" : {
             |      "lastUsed": "${OffsetDateTime.now()}"
             |   }
             |}
         """.stripMargin
        val action = new Update.Builder(update)
          .index(IndexName)
          .`type`(Types.ApiKey)
          .id(keyId)
          .build()
        val result = jest.execute(action)
        if (!result.isSucceeded)
          logger.warn(s"Failed to update last-used timestamp for API key. Error: ${result.getErrorMessage}")
      }

      def disable(keyId: String): Id[Unit] =
        executeAndRefresh(updateActiveFlag(keyId, active = false)).run(jest)

      def enable(keyId: String): Id[Unit] =
        executeAndRefresh(updateActiveFlag(keyId, active = true)).run(jest)

      def delete(keyId: String): Id[Boolean] =
        executeAndRefresh(_delete(keyId)).run(jest)
    }
}
