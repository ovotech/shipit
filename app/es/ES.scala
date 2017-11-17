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
import io.searchbox.indices.{CreateIndex, DeleteIndex, IndicesExists, Refresh}
import models._
import play.api.Logger

import scala.collection.JavaConverters._

object ES {

  private val IndexName = "shipit"
  private object Types {
    val ApiKey     = "apikey"
    val Deployment = "deployment"
  }

  private val PageSize                = 20
  private def pageToOffset(page: Int) = (page - 1) * PageSize

  case class Page[A](items: Seq[A], pageNumber: Int, total: Int) {

    def lastPage: Int = ((total - 1) / PageSize) + 1

  }

  object Deployments {

    def create(deployment: Deployment): Reader[JestClient, Identified[Deployment]] =
      executeAndRefresh(_create(deployment))

    def search(
        teamQuery: Option[String],
        serviceQuery: Option[String],
        buildIdQuery: Option[String],
        resultQuery: Option[DeploymentResult],
        page: Int
    ) = Reader[JestClient, Page[Identified[Deployment]]] { jest =>
      val filters = Seq(
        teamQuery.map(x => s"""{ "match": { "team": "$x" } }"""),
        serviceQuery.map(x => s"""{ "match": { "service": "$x" } }"""),
        buildIdQuery.map(x => s"""{ "match": { "buildId": "$x" } }"""),
        resultQuery.map(x => s"""{ "match": { "result": "$x" } }""")
      ).flatten
      val query =
        s"""{
           |  "from": ${pageToOffset(page)},
           |  "size": $PageSize,
           |  "query": {
           |    "bool": {
           |      "must": { "match_all": {} },
           |      "filter": {
           |        "bool": {
           |          "must": [
           |            ${filters.mkString(", ")}
           |          ]
           |        }
           |      }
           |    }
           |  }
           |}""".stripMargin
      val action = new Search.Builder(query)
        .addIndex(IndexName)
        .addType(Types.Deployment)
        .addSort(new Sort("timestamp", Sorting.DESC))
        .build()
      val result = jest.execute(action)
      val items = result
        .getHits(classOf[JsonElement])
        .asScala
        .flatMap(hit => parseHit(hit.source, hit.id))
      Page(items, page, result.getTotal.toInt)
    }

    def delete(id: String): Reader[JestClient, Either[String, Unit]] = executeAndRefresh(_delete(id))

    private def _create(deployment: Deployment) = Reader[JestClient, Identified[Deployment]] { jest =>
      val linksList = deployment.links.map { link =>
        Map(
          "title" -> link.title,
          "url"   -> link.url
        ).asJava
      }.asJava
      val map = Map(
        "team"      -> deployment.team,
        "service"   -> deployment.service,
        "buildId"   -> deployment.buildId,
        "timestamp" -> deployment.timestamp.toString,
        "links"     -> linksList,
        "result"    -> deployment.result.toString
      ) ++
        deployment.note.map("note"                   -> _) ++
        deployment.jiraComponent.map("jiraComponent" -> _)

      val action = new Index.Builder(map.asJava)
        .index(IndexName)
        .`type`(Types.Deployment)
        .build()
      val esResult = jest.execute(action)
      val id       = esResult.getId
      Identified(id, deployment)
    }

    private def parseHit(jsonElement: JsonElement, id: String): Option[Identified[Deployment]] = {
      decode[Deployment](jsonElement.toString)
        .leftMap(e => Logger.warn("Failed to decode deployment returned by ES", e))
        .map(value => Identified(id, value))
        .toOption
    }

    private def _delete(id: String) = Reader[JestClient, Either[String, Unit]] { jest =>
      val action = new Delete.Builder(id)
        .index(IndexName)
        .`type`(Types.Deployment)
        .build()
      val result = jest.execute(action)
      if (result.isSucceeded)
        Right(())
      else
        Left(result.getErrorMessage)
    }

  }

  object ApiKeys {

    def create(key: String, description: Option[String], createdBy: String): Reader[JestClient, ApiKey] =
      executeAndRefresh(_create(key, description, createdBy))

    private def _create(key: String, description: Option[String], createdBy: String) = Reader[JestClient, ApiKey] {
      jest =>
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
        val result = jest.execute(action)
        val id     = result.getId
        ApiKey(id, key, description, createdAt, createdBy, active = true)
    }

    def findByKey(key: String) = Reader[JestClient, Option[ApiKey]] { jest =>
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

    def list(createdBy: String, page: Int) = Reader[JestClient, Seq[ApiKey]] { jest =>
      val query =
        s"""{
           |  "from": ${pageToOffset(page)},
           |  "size": $PageSize,
           |  "query": { "term": { "createdBy": "$createdBy" } }
           |}""".stripMargin
      val action = new Search.Builder(query)
        .addIndex(IndexName)
        .addType(Types.ApiKey)
        .addSort(new Sort("createdAt", Sorting.DESC))
        .build()
      val result = jest.execute(action)
      result
        .getHits(classOf[JsonElement])
        .asScala
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
        json       <- parse(jsonElement.toString).right
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

  private def executeAndRefresh[A](action: Reader[JestClient, A]): Reader[JestClient, A] =
    for {
      result <- action
      _      <- refresh
    } yield result

  def initIndex: Reader[JestClient, Unit] = {
    for {
      alreadyExists <- doesIndexExist
      _             <- createIndex(alreadyExists)
    } yield ()
  }

  private def doesIndexExist = Reader[JestClient, Boolean] { jest =>
    jest.execute(new IndicesExists.Builder(IndexName).build()).getResponseCode == 200
  }

  private def createIndex(alreadyExists: Boolean) = Reader[JestClient, Unit] { jest =>
    if (!alreadyExists) {
      jest.execute(new CreateIndex.Builder(IndexName).settings(s"""
           |{
           |  "settings" : {
           |    "number_of_shards" : 1,
           |    "number_of_replicas" : 1
           |  },
           |  "mappings" : {
           |    "${Types.ApiKey}" : {
           |      "properties" : {
           |        "key" : { "type" : "string", "index" : "not_analyzed" },
           |        "description" : { "type" : "string" },
           |        "createdAt" : { "type" : "date" },
           |        "createdBy" : { "type" : "string", "index": "not_analyzed" },
           |        "active" : { "type" : "boolean" }
           |      }
           |    },
           |    "${Types.Deployment}": {
           |      "properties" : {
           |        "team" : { "type" : "string" },
           |        "service" : { "type" : "string" },
           |        "buildId" : { "type" : "string", "index" : "not_analyzed" },
           |        "timestamp" : { "type" : "date" },
           |        "links": {
           |          "properties": {
           |            "title": { "type" : "string" },
           |            "url": { "type" : "string" }
           |          }
           |        },
           |        "result" : { "type" : "string", "index": "not_analyzed" }
           |      }
           |    }
           |  }
           |}
         """.stripMargin).build())
      Logger.info("Created ES index")
    }
  }

//  def deleteIndex = Reader[JestClient, Unit] { jest =>
//    jest.execute(new DeleteIndex.Builder(IndexName).build())
//  }

}
