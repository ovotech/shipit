package jira

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import cats.data.Kleisli
import cats.instances.future._
import cats.syntax.option._
import models.Deployment
import models.DeploymentResult.{Cancelled, Failed, Succeeded}
import play.Logger
import play.api.libs.json.Json.obj
import play.api.libs.json._
import play.api.libs.ws.{WSAuthScheme, WSClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object JIRA {

  case class Context(wsClient: WSClient,
                     browseTicketsUrl: String,
                     issueApiUrl: String,
                     username: String,
                     password: String)

  case class CreateIssueKey(key: String)

  implicit val issueReads = Json.reads[CreateIssueKey]

  def createIssueIfPossible(deployment: Deployment): Kleisli[Future, Context, Option[CreateIssueKey]] = {
    deployment.jiraComponent match {
      case Some(jiraComponent) => {

        for {
          issueKey <- createIssue(deployment, jiraComponent)
          _        <- JiraTransitions.transition(issueKey, "Standard Change Approved")
          _        <- JiraTransitions.transition(issueKey, "Implemented")
        } yield issueKey

      }
      case None => Kleisli.pure[Future, Context, Option[CreateIssueKey]](None)
    }
  }

  def createIssue(deployment: Deployment, jiraComponent: String) = Kleisli[Future, Context, Option[CreateIssueKey]] {
    ctx =>
      val json = buildPayload(deployment, jiraComponent, OffsetDateTime.now())
      ctx.wsClient
        .url(ctx.issueApiUrl)
        .withAuth(ctx.username, ctx.password, WSAuthScheme.BASIC)
        .withHeaders("Content-Type" -> "application/json")
        .post(json)
        .map(_.json.validate[CreateIssueKey] match {
          case JsSuccess(issueKey, _) => issueKey.some
          case JsError(errors) =>
            Logger.error(s"Failed to deserialize jira CreateIssueKey: $errors")
            None
        })
  }

  def buildPayload(deployment: Deployment, jiraComponent: String, currentTime: OffsetDateTime): JsValue = {
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

    val formattedTime = currentTime.format(DateTimeFormatter.ISO_DATE_TIME)

    obj(
      "fields" -> obj(
        "project"           -> obj("key" -> "REL"),
        "summary"           -> summary,
        "description"       -> description,
        "issuetype"         -> obj("name" -> "Standard Change"),
        "components"        -> List(obj("name" -> jiraComponent)),
        "assignee"          -> obj("name" -> "osp-service"),
        "customfield_10302" -> formattedTime,
        "customfield_11201" -> formattedTime,
        "customfield_10500" -> "UAT / peer review",
        "customfield_10301" -> "Deploy using build pipeline. See links for more details",
        "customfield_10300" -> "Back out using build pipeline. See links for more details"
      )
    )
  }

}
