package datadog

import cats.data.Kleisli
import deployments.Deployment
import play.api.libs.json.JsValue
import play.api.libs.json.Json.{arr, obj}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.Future

object Datadog {

  private val url = "https://api.datadoghq.com/api/v1/events"

  case class Context(wsClient: WSClient, apiKey: String)

  def sendEvent(deployment: Deployment) = Kleisli[Future, Context, WSResponse] { ctx =>
    val json = buildPayload(deployment)
    ctx.wsClient
      .url(url)
      .addQueryStringParameters("api_key" -> ctx.apiKey)
      .post(json)
  }

  def buildPayload(deployment: Deployment): JsValue =
    obj(
      "title" -> s"Deployment: ${deployment.team}/${deployment.service}",
      "text"  -> s"Service [${deployment.team}/${deployment.service}] was deployed successfully.",
      "tags" -> arr(
        "shipit:deployment",
        s"team:${deployment.team}",
        s"service:${deployment.service}",
        s"env:${deployment.environment.name}",
        s"build_id:${deployment.buildId}"
      )
    )

}
