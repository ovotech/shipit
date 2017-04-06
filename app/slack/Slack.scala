package slack

import cats.data.Kleisli
import models.Deployment
import models.DeploymentResult.{Cancelled, Failed, Succeeded}
import play.api.Logger
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.Future

object Slack {

  case class Context(wsClient: WSClient, webhookUrl: String)

  def sendNotification(deployment: Deployment) = Kleisli[Future, Context, WSResponse] { ctx =>
    val json = buildPayload(deployment)
    ctx.wsClient.url(ctx.webhookUrl).post(Map("payload" -> Seq(json)))
  }

  private case class Field(title: String, value: String) {
    override def toString =
      s"""
         |{
         |  "title":"$title",
         |  "value":"$value",
         |  "short":true
         |}
         |
       """.stripMargin
  }

  def buildPayload(deployment: Deployment): String = {
    val (message, colour) = deployment.result match {
      case Succeeded =>
        (s"Service [${deployment.team}/${deployment.service}] was deployed successfully.", "#00D000")
      case Failed =>
        (s"Deployment of service [${deployment.team}/${deployment.service}] failed.", "#D00000")
      case Cancelled =>
        (s"Deployment of service [${deployment.team}/${deployment.service}] was cancelled.", "#DDDD00")
    }
    val buildIdField = Field("Build ID", deployment.buildId)
    val linksField = {
      val value = deployment.links
        .map(link => s"<${link.url}|${link.title}>")
        .mkString(", ")
      Field("Links", value)
    }
    val notesField: Option[Field] = deployment.note.map(Field("Notes", _))
    val fields                    = List(Some(buildIdField), Some(linksField), notesField).flatten.mkString(",")
    s"""
       |{
       |  "attachments":[
       |    {
       |      "fallback":"$message <https://shipit.ovotech.org.uk/deployments|See recent deployments>",
       |      "pretext":"$message <https://shipit.ovotech.org.uk/deployments|See recent deployments>",
       |      "color":"$colour",
       |      "fields":[$fields]
       |    }
       |  ]
       |}
     """.stripMargin
  }

}
