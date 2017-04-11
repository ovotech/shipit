package jira

import cats.data.Kleisli
import cats.syntax.option._
import cats.instances.future._

import models.Deployment
import models.DeploymentResult.{Cancelled, Failed, Succeeded}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.json.Json.obj
import play.api.libs.ws.{WSAuthScheme, WSClient, WSResponse}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object JIRA {

  case class Context(wsClient: WSClient, createIssueApiUrl: String, username: String, password: String)

  def createIssueIfPossible(deployment: Deployment): Kleisli[Future, Context, Option[WSResponse]] = {
    deployment.jiraComponent match {
      case Some(jiraComponent) => createIssue(deployment, jiraComponent).map(_.some)
      case None                => Kleisli.pure[Future, Context, Option[WSResponse]](None)
    }
  }

  def createIssue(deployment: Deployment, jiraComponent: String) = Kleisli[Future, Context, WSResponse] { ctx =>
    val json = buildPayload(deployment, jiraComponent)
    ctx.wsClient
      .url(ctx.createIssueApiUrl)
      .withAuth(ctx.username, ctx.password, WSAuthScheme.BASIC)
      .withHeaders("Content-Type" -> "application/json")
      .post(json)
  }

  def buildPayload(deployment: Deployment, jiraComponent: String): JsValue = {
    val summary = deployment.result match {
      case Succeeded =>
        s"Service '${deployment.team}/${deployment.service}' was deployed successfully."
      case Failed =>
        s"Deployment of service '${deployment.team}/${deployment.service}' failed."
      case Cancelled =>
        s"Deployment of service '${deployment.team}/${deployment.service}' was cancelled."
    }
    val linksText = {
      if (deployment.links.isEmpty) ""
      else {
        val list = deployment.links
          .map(link => s"* [${link.title}|${link.url}]")
          .mkString("\n")
        s"Links:\n$list"
      }
    }
    val notesText: String = deployment.note.fold[String]("")(n => s"Notes:\n$n")
    val description =
      s"""$summary
         |
         |$linksText
         |
         |$notesText
         |
         |This ticket was created by :shipit: - [See recent deployments|https://shipit.ovotech.org.uk/deployments]""".stripMargin

    obj(
      "fields" -> obj(
        "project"     -> obj("key" -> "REL"),
        "summary"     -> summary,
        "description" -> description,
        "issuetype"   -> obj("name" -> "Standard Change"),
        "components"  -> List(obj("name" -> jiraComponent))
      )
    )
  }

}
