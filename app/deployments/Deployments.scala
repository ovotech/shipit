package deployments

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.instances.list._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.tagless.FunctorK
import com.sksamuel.elastic4s.ElasticDsl.{createIndex, properties, _}
import com.sksamuel.elastic4s.cats.effect.instances._
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.fields.{KeywordField, NestedField}
import com.sksamuel.elastic4s.requests.indexes.CreateIndexRequest
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchQuery
import com.sksamuel.elastic4s.{ElasticClient, Executor}
import elasticsearch.CirceCodecs._
import elasticsearch.Pagination._
import elasticsearch.Instances._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import models.{Identified, Service}

trait Deployments[F[_]] {
  def create(deployment: Deployment): F[Identified[Deployment]]
  def search(terms: SearchTerms, page: Int): F[Page[Identified[Deployment]]]
  def recent(deployedInLastNDays: Int): F[Seq[Service]]
  def delete(id: String): F[Either[String, Unit]]
  def createIndex: F[Unit]
}

object Deployments {

  val indexName: String                  = "shipit_v3_deployments"
  implicit val fk: FunctorK[Deployments] = cats.tagless.Derive.functorK

  private def searchFilters(terms: SearchTerms): Option[NonEmptyList[MatchQuery]] =
    NonEmptyList.fromList(
      terms.buildId.map(b => matchQuery("buildId", b)).toList ++
        terms.service.map(b => matchQuery("service", b)).toList ++
        terms.team.map(b => matchQuery("team", b)).toList
    )

  private def searchQuery(terms: SearchTerms, page: Int): SearchRequest = {
    val basicQuery = search(indexName).from(pageToOffset(page)).limit(PageSize).matchAllQuery()
    searchFilters(terms).fold(basicQuery)(f => basicQuery.postFilter(must(f.head, f.tail: _*)))
  }

  private def recentQuery(deployedInLastNDays: Int): SearchRequest =
    search(indexName)
      .query(rangeQuery("timestamp").gte(s"now-${deployedInLastNDays}d/d"))
      .size(0)
      .aggs(
        termsAgg("by_team", "team")
          .size(500)
          .addSubagg(
            termsAgg("by_service", "service")
              .size(500)
              .addSubagg(maxAgg("last_deployment", "timestamp"))
          )
      )

  private val createDeployments: CreateIndexRequest =
    createIndex(indexName)
      .mapping(
        properties(
          KeywordField("team"),
          KeywordField("service"),
          KeywordField("buildId"),
          KeywordField("timestamp"),
          KeywordField("note"),
          NestedField(
            name = "links",
            properties = List(
              KeywordField("title"),
              KeywordField("url")
            )
          )
        )
      )

  def apply[F[_]: Executor](client: ElasticClient)(implicit F: Sync[F]): Deployments[F] =
    new Deployments[F] {

      implicit val linkEnc: Encoder[Link]      = deriveEncoder
      implicit val linkDec: Decoder[Link]      = deriveDecoder
      implicit val depEnc: Encoder[Deployment] = deriveEncoder[Deployment]
      implicit val depDec: Decoder[Deployment] = deriveDecoder[Deployment]

      def createIndex: F[Unit] =
        client.execute(createDeployments).void

      def create(dep: Deployment): F[Identified[Deployment]] =
        client.execute(indexInto(indexName).source(dep)).map(r => Identified(r.result.id, dep))

      def search(terms: SearchTerms, page: Int): F[Page[Identified[Deployment]]] =
        for {
          response <- client.execute(searchQuery(terms, page))
          data     <- response.result.safeTo[Identified[Deployment]].toList.traverse(F.fromTry)
        } yield Page(data, page, response.result.totalHits.toInt)

      def recent(deployedInLastNDays: Int): F[Seq[Service]] =
        for {
          response <- client.execute(recentQuery(deployedInLastNDays))
          _ = println(response.result.aggs)
        } yield Seq.empty

      def delete(id: String): F[Either[String, Unit]] =
        client.execute(deleteById(indexName, id)).map(_.toEither.bimap(_.reason, _ => ()))
    }
}
