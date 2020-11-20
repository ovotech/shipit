package controllers

import apikeys.{ApiKeys, ExistingApiKey}
import cats.data.EitherT
import models.Identified
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class ApiKeyAuth(apiKeys: ApiKeys[Future], actionBuilder: DefaultActionBuilder)(implicit ec: ExecutionContext) {

  object CheckApiKey extends ActionFilter[Request] with Results {

    def executionContext: ExecutionContext = ec

    def findParameter(request: Request[_]): Either[Result, String] =
      request.getQueryString("apikey").toRight(Unauthorized("You must provide an 'apikey' query parameter"))

    def findInElasticSearch(key: String): Future[Either[Result, Identified[ExistingApiKey]]] =
      apiKeys.findByKey(key).map(_.filter(_.value.active).toRight(Unauthorized("Invalid API Key")))

    protected def filter[A](request: Request[A]): Future[Option[Result]] =
      EitherT
        .fromEither[Future](findParameter(request))
        .flatMap(k => EitherT(findInElasticSearch(k)))
        .semiflatMap(k => apiKeys.updateLastUsed(k.id))
        .swap
        .toOption
        .value
  }

  val ApiKeyAuthAction: ActionBuilder[Request, AnyContent] =
    actionBuilder andThen CheckApiKey
}
