package es

import java.time.OffsetDateTime

import io.circe.parser._
import io.circe.generic.auto._
import CirceDecoders._
import cats.syntax.either._
import cats.data.Reader
import com.google.gson.JsonElement
import io.searchbox.client.JestClient
import io.searchbox.core.{Delete, Index, Search, Update}
import io.searchbox.core.search.sort.Sort
import io.searchbox.core.search.sort.Sort.Sorting
import io.searchbox.indices.{CreateIndex, IndicesExists, Refresh}
import models._
import play.api.Logger

import scala.collection.JavaConverters._

object ES {

  private val IndexName = "shipit"
  private object Types {
    val ApiKey = "apikey"
    val Deployment = "deployment"
  }

  object Deployments {

    def create(team: String,
               service: String,
               buildId: String,
               timestamp: OffsetDateTime,
               links: Seq[Link],
               result: DeploymentResult): Reader[JestClient, Deployment] =
      executeAndRefresh(_create(team, service, buildId, timestamp, links, result))

    def _create(team: String,
                service: String,
                buildId: String,
                timestamp: OffsetDateTime,
                links: Seq[Link],
                result: DeploymentResult) = Reader[JestClient, Deployment] { jest =>
      val linksList = links.map { link =>
        Map(
          "title" -> link.title,
          "url" -> link.url
        ).asJava
      }.asJava
      val map = Map(
        "team" -> team,
        "service" -> service,
        "buildId" -> buildId,
        "timestamp" -> timestamp.toString,
        "links" -> linksList,
        "result" -> result.toString
      )
      val action = new Index.Builder(map.asJava)
        .index(IndexName)
        .`type`(Types.Deployment)
        .build()
      val esResult = jest.execute(action)
      val id = esResult.getId
      Deployment(id, team, service, buildId, timestamp, links, result)
    }

  }

  object ApiKeys {

    def create(key: String,
               description: Option[String],
               createdBy: String): Reader[JestClient, ApiKey] =
      executeAndRefresh(_create(key, description, createdBy))

    def _create(key: String,
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

    def disable(keyId: String) = executeAndRefresh(updateActiveFlag(keyId, active = false))

    def enable(keyId: String) = executeAndRefresh(updateActiveFlag(keyId, active = true))

    def delete(keyId: String) = executeAndRefresh(_delete(keyId))

    private def _delete(keyId: String) = Reader[JestClient, Unit] { jest =>
      val action = new Delete.Builder(keyId)
        .index(IndexName)
        .`type`(Types.ApiKey)
        .build()
      jest.execute(action)
    }

    private def updateActiveFlag(keyId: String, active: Boolean) = Reader[JestClient, Unit] { jest =>
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

  private def refresh = Reader[JestClient, Unit] { jest =>
    jest.execute(new Refresh.Builder().addIndex(IndexName).build())
  }

  private def executeAndRefresh[A](action: Reader[JestClient, A]): Reader[JestClient, A] = for {
    result <- action
    _ <- refresh
  } yield result

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
