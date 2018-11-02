package controllers

import es.ES
import io.searchbox.client.JestClient
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class ApiKeyAuth(jestClient: JestClient, actionBuilder: DefaultActionBuilder)(implicit ec: ExecutionContext) {

  object CheckApiKey extends ActionFilter[Request] with Results {

    override def executionContext: ExecutionContext = ec

    override protected def filter[A](request: Request[A]): Future[Option[Result]] = Future {
      request.getQueryString("apikey") match {
        case Some(key) =>
          ES.ApiKeys.findByKey(key).run(jestClient) match {
            case Some(apiKey) if apiKey.active =>
              // OK, update last-used timestamp for API key and allow request to proceed
              ES.ApiKeys.updateLastUsed(key).run(jestClient)
              None
            case _ =>
              Some(Unauthorized("Invalid API key"))
          }
        case None =>
          Some(Unauthorized("You must provide an 'apikey' query parameter"))
      }
    }
  }

  val ApiKeyAuthAction: ActionBuilder[Request, AnyContent] = actionBuilder andThen CheckApiKey

}
