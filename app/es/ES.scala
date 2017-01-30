package es

import java.time.OffsetDateTime

import io.circe.parser._
import io.circe.generic.auto._
import CirceDecoders._
import cats.syntax.either._
import cats.data.Reader
import com.google.gson.JsonElement
import io.searchbox.client.JestClient
import io.searchbox.core.{Index, Search}
import io.searchbox.core.search.sort.Sort
import io.searchbox.core.search.sort.Sort.Sorting
import io.searchbox.indices.{CreateIndex, IndicesExists}
import models.ApiKey
import play.api.Logger

import scala.collection.JavaConverters._

object ES {

  private val IndexName = "shipit"
  private object Types {
    val ApiKey = "apikey"
    val Deployment = "deployment"
  }

  object ApiKeys {

    def create(key: String,
               description: Option[String],
               createdBy: String) = Reader[JestClient, ApiKey] { jest =>
      val createdAt = OffsetDateTime.now()
      val map = Map(
        "key" -> key,
        "createdBy" -> createdBy,
        "createdAt" -> createdAt.toString,
        "active" -> true
      ) ++ description.map("description" -> _)
      val action = new Index.Builder(map.asJava)
        .index(IndexName)
        .`type`(Types.ApiKey)
        .build()
      val result = jest.execute(action)
      val id = result.getId
      ApiKey(id, key, description, createdAt, createdBy, active = true)
    }

    def list(offset: Int) = Reader[JestClient, Seq[ApiKey]] { jest =>
      val query =
        s"""{
           |  "from": $offset,
           |  "size": 20,
           |  "query": { "match_all": {} }
           |}""".stripMargin
      val action = new Search.Builder(query)
        .addIndex(IndexName)
        .addType(Types.ApiKey)
        .addSort(new Sort("createdAt", Sorting.DESC))
        .build()
      val result = jest.execute(action)
      result.getHits(classOf[JsonElement]).asScala
        .flatMap(hit => parseHit(hit.source, hit.id))
    }

    private def parseHit(jsonElement: JsonElement, id: String): Option[ApiKey] = {
      val either = for {
        json <- parse(jsonElement.toString).right
        incomplete <- json.as[String => ApiKey].right
      } yield incomplete.apply(id)
      either
        .leftMap(e => Logger.warn("Failed to decode API key returned by ES", e))
        .toOption
    }

  }

  def initIndex: Reader[JestClient, Unit] = {
    for {
      alreadyExists <- doesIndexExist
      _ <- createIndex(alreadyExists)
    } yield ()
  }

  private def doesIndexExist = Reader[JestClient, Boolean] { jest =>
    jest.execute(new IndicesExists.Builder(IndexName).build()).getResponseCode == 200
  }

  private def createIndex(alreadyExists: Boolean) = Reader[JestClient, Unit] { jest =>
    if (!alreadyExists) {
      jest.execute(new CreateIndex.Builder(IndexName).build()) // TODO mapping
      Logger.info("Created ES index")
    }
  }

}
