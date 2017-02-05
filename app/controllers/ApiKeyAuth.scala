package controllers

import es.ES
import io.searchbox.client.JestClient
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait ApiKeyAuth {

  def jestClient: JestClient

  object CheckApiKey extends ActionFilter[Request] with Results {
    override protected def filter[A](request: Request[A]): Future[Option[Result]] = Future {
      request.getQueryString("apikey") match {
        case Some(key) =>
          ES.ApiKeys.findByKey(key).run(jestClient) match {
            case Some(apiKey) if apiKey.active =>
              // OK, allow request to proceed
              None
            case _ =>
              Some(Unauthorized("Invalid API key"))
          }
        case None =>
          Some(Unauthorized("You must provide an 'apikey' query parameter"))
      }
    }
  }

  val ApiKeyAuthAction = Action andThen CheckApiKey
}
