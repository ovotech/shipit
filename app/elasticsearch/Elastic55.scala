package elasticsearch

import cats.data.Reader
import cats.effect.{ContextShift, IO}
import io.searchbox.action.Action
import io.searchbox.client.{JestClient, JestResult, JestResultHandler}
import io.searchbox.indices.{CreateIndex, IndicesExists, Refresh}

import scala.concurrent.{Future, Promise}
import cats.syntax.functor._
import scala.util.{Failure, Success}

object Elastic55 {

  val IndexName = "shipit_v2"

  object Types {
    val ApiKey     = "apikey"
    val Deployment = "deployment"
  }

  val PageSize                = 20
  def pageToOffset(page: Int): Int = (page - 1) * PageSize

  case class Page[A](items: Seq[A], pageNumber: Int, total: Int) {
    def lastPage: Int = ((total - 1) / PageSize) + 1
  }

  implicit class JestOps(jestClient: JestClient) {

    def refresh(implicit cs: ContextShift[IO]): IO[Unit] =
      execIO(new Refresh.Builder().addIndex(IndexName).build()).void

    def execIO[A <: JestResult](req: Action[A])(implicit cs: ContextShift[IO]): IO[A] =
      IO.fromFuture(IO(exec(req)))

    def exec[A <: JestResult](req: Action[A]): Future[A] = {
      val p: Promise[A] = Promise[A]()
      jestClient.executeAsync(req, new JestResultHandler[A] {
        def completed(result: A): Unit = p.complete(Success(result))
        def failed(ex: Exception): Unit = p.complete(Failure(ex))
      })
      p.future
    }
  }

  def initIndex: Reader[JestClient, Unit] = {
    for {
      alreadyExists <- doesIndexExist
      _             <- createIndex(alreadyExists)
    } yield ()
  }

  def doesIndexExist: Reader[JestClient, Boolean] = Reader[JestClient, Boolean] { jest =>
    jest.execute(new IndicesExists.Builder(IndexName).build()).getResponseCode == 200
  }

  def createIndex(alreadyExists: Boolean): Reader[JestClient, Unit] = Reader[JestClient, Unit] { jest =>
    if (!alreadyExists) {
      val settings =
        s"""
           |{
           |  "number_of_shards" : 1,
           |  "number_of_replicas" : 1
           |}
        """.stripMargin
      val mappings =
        s"""
           |{
           |  "${Types.ApiKey}" : {
           |    "properties" : {
           |      "key" : { "type" : "keyword" },
           |      "description" : { "type" : "text" },
           |      "createdAt" : { "type" : "date" },
           |      "createdBy" : { "type" : "keyword" },
           |      "active" : { "type" : "boolean" },
           |      "lastUsed" : { "type" : "date" }
           |    }
           |  },
           |  "${Types.Deployment}": {
           |    "properties" : {
           |      "team" : { "type" : "keyword" },
           |      "service" : { "type" : "keyword" },
           |      "buildId" : { "type" : "keyword" },
           |      "timestamp" : { "type" : "date" },
           |      "links": {
           |        "properties": {
           |          "title": { "type" : "keyword" },
           |          "url": { "type" : "keyword" }
           |        }
           |      }
           |    }
           |  }
           |}
         """.stripMargin
      val result = jest.execute(new CreateIndex.Builder(IndexName).settings(settings).mappings(mappings).build())

      logger.info(s"Created ES index. Result: $result")
    }
  }

//  def deleteIndex = Reader[JestClient, Unit] { jest =>
//    jest.execute(new DeleteIndex.Builder(IndexName).build())
//  }

}
