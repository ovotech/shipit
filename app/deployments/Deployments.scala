package deployments

import java.time.{Instant, OffsetDateTime, ZoneOffset}

import cats.effect.{ContextShift, IO}
import cats.syntax.either._
import cats.syntax.flatMap._
import com.google.gson.JsonElement
import elasticsearch.Elastic55._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import io.searchbox.client.JestClient
import io.searchbox.core.search.sort.Sort
import io.searchbox.core.search.sort.Sort.Sorting
import io.searchbox.core.{Delete, Index, Search}
import models.{Identified, Service}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

trait Deployments[F[_]] {
  def create(deployment: Deployment): F[Identified[Deployment]]
  def search(terms: SearchTerms, page: Int): F[Page[Identified[Deployment]]]
  def recent(deployedInLastNDays: Int): F[Seq[Service]]
  def delete(id: String): F[Either[String, Unit]]
}

object Deployments {

  def old(jest: JestClient)(implicit cs: ContextShift[IO]): Deployments[IO] =
    new Deployments[IO] {

      val logger: Logger =
        LoggerFactory.getLogger("shipit.deployments.Deployments.old")

      implicit val decoder: Decoder[Deployment] =
        deriveDecoder[Deployment]

      def _create(deployment: Deployment): IO[Identified[Deployment]] = {
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
          "links"     -> linksList
        ) ++
          deployment.note.map("note" -> _)

        val action = new Index.Builder(map.asJava)
          .index(IndexName)
          .`type`(Types.Deployment)
          .build()

        jest.execIO(action).map(r => Identified(r.getId, deployment))
      }

      def _delete(id: String): IO[Either[String, Unit]] = {
        val action = new Delete.Builder(id)
          .index(IndexName)
          .`type`(Types.Deployment)
          .build()
        jest.execIO(action).map { result =>
          if (result.isSucceeded)
            Right(())
          else
            Left(result.getErrorMessage)
        }
      }

      def parseHit(jsonElement: JsonElement, id: String): Option[Identified[Deployment]] =
        decode[Deployment](jsonElement.toString)
          .leftMap(e => logger.warn("Failed to decode deployment returned by ES", e))
          .map(value => Identified(id, value))
          .toOption

      def create(deployment: Deployment): IO[Identified[Deployment]] =
        _create(deployment).flatTap(_ => jest.refresh)

      def search(terms: SearchTerms, page: Int): IO[Page[Identified[Deployment]]] = {
        val filters = Seq(
          terms.team.map(x => s"""{ "match": { "team": "$x" } }"""),
          terms.service.map(x => s"""{ "match": { "service": "$x" } }"""),
          terms.buildId.map(x => s"""{ "match": { "buildId": "$x" } }""")
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

             |}""".
            stripMargin

        val action = new Search.Builder(query)
          .addIndex(IndexName)
          .addType(Types.Deployment)
          .addSort(new Sort ( "timestamp", Sorting.DESC))
          .build()

        jest.execIO(action).map { result =>
          val items =
            result
              .getHits(classOf[JsonElement])
              .asScala
              .flatMap(hit => parseHit(hit.source, hit.id))
          Page(items, page, result.getTotal.toInt)
        }
      }

      def recent(deployedInLastNDays: Int): IO[Seq[Service]] = {
        val query =
          s"""
             |{
             |  "query": {
             |    "range": {
             |      "timestamp": {
             |        "gte" : "now-${deployedInLastNDays}d/d"
             |      }
             |    }
             |  },
             |  "size": 0,
             |  "aggs": {
             |    "by_team": {
             |      "terms": {
             |        "field": "team",
             |        "size": 500
             |      },
             |      "aggs": {
             |        "by_service": {
             |          "terms": {
             |            "field": "service",
             |            "size": 500
             |          },
             |          "aggs": {
             |            "last_deployment": {
             |              "max": {
             |                "field": "timestamp"
             |              }
             |            }
             |          }
             |        }
             |      }
             |    }
             |  }
             |}
        """.stripMargin

        val action = new Search.Builder(query)
          .addIndex(IndexName)
          .addType(Types.Deployment)
          .build()

        jest.execIO(action).map { result =>

          val teamBuckets = result.getAggregations.getTermsAggregation("by_team").getBuckets.asScala

          val services = for {
            team <- teamBuckets
            services = team.getTermsAggregation("by_service").getBuckets.asScala
            service <- services
          } yield {
            val lastDeploymentEpochMillis = service.getMaxAggregation("last_deployment").getMax.toLong
            val lastDeployment = OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastDeploymentEpochMillis), ZoneOffset.UTC)
            Service(team.getKey, service.getKey, lastDeployment)
          }

          services.sortBy(_.team)
        }
      }

      def delete(id: String): IO[Either[String, Unit]] = {
        _delete(id).flatTap(_ => jest.refresh)
      }
    }

}


