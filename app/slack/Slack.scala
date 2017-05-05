package slack

import cats.data.Kleisli
import models.Deployment
import models.DeploymentResult.{Cancelled, Failed, Succeeded}
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.libs.json.Json.{arr, obj}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.Future

object Slack {

  case class Context(wsClient: WSClient, webhookUrl: String)

  def sendNotification(deployment: Deployment, channel: Option[String]) = Kleisli[Future, Context, WSResponse] { ctx =>
    val json = buildPayload(deployment, channel)
    ctx.wsClient.url(ctx.webhookUrl).post(Map("payload" -> Seq(Json.stringify(json))))
  }

  def buildPayload(deployment: Deployment, channel: Option[String]): JsValue = {
    val (message, colour) = deployment.result match {
      case Succeeded =>
        (s"Service [${deployment.team}/${deployment.service}] was deployed successfully.", "#00D000")
      case Failed =>
        (s"Deployment of service [${deployment.team}/${deployment.service}] failed.", "#D00000")
      case Cancelled =>
        (s"Deployment of service [${deployment.team}/${deployment.service}] was cancelled.", "#DDDD00")
    }

    val fields = buildFields(deployment)

    val withoutChannel = obj(
      "attachments" -> arr(
        obj(
          "fallback" -> s"$message <https://shipit.ovotech.org.uk/deployments|See recent deployments>",
          "pretext"  -> s"$message <https://shipit.ovotech.org.uk/deployments|See recent deployments>",
          "color"    -> colour,
          "fields"   -> fields
        )
      )
    )

    channel.fold[JsValue](withoutChannel)(channel => withoutChannel + ("channel" -> JsString(channel)))
  }

  private def buildFields(deployment: Deployment) = {
    def field(title: String, value: String, short: Boolean) = obj(
      "title" -> title,
      "value" -> value,
      "short" -> short
    )

    val buildIdField = field("Build ID", deployment.buildId, short = true)

    val linksField = {
      val value = deployment.links
        .map(link => s"<${link.url}|${link.title}>")
        .mkString(", ")
      field("Links", value, short = true)
    }

    val notesField = deployment.note.map(note => field("Notes", note, short = false))

    List(Some(buildIdField), Some(linksField), notesField).flatten
  }

}
