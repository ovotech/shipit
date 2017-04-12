package logic

import java.time.OffsetDateTime

import cats.Id
import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.instances.future._
import com.gu.googleauth.UserIdentity
import es.ES
import io.searchbox.client.JestClient
import jira.JIRA
import models.DeploymentResult.Succeeded
import models._
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.ws.WSResponse
import slack.Slack

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Deployments {

  case class Context(jestClient: JestClient,
                     slackCtx: Slack.Context,
                     jiraCtx: JIRA.Context,
                     isAdmin: UserIdentity => Boolean)

  def createDeployment(team: String,
                       service: String,
                       jiraComponent: Option[String],
                       buildId: String,
                       timestamp: OffsetDateTime,
                       links: Seq[Link],
                       note: Option[String],
                       result: DeploymentResult): Kleisli[Future, Context, Deployment] = {

    val deployment = Deployment(None, team, service, jiraComponent, buildId, timestamp, links, note, result)

    for {
      jiraResp     <- JIRA.createIssueIfPossible(deployment).local[Context](_.jiraCtx)
      updatedLinks <- buildLinks(deployment, jiraResp).local[Context](_.jiraCtx)
      esDeployment <- ES.Deployments
        .create(team, service, jiraComponent, buildId, timestamp, updatedLinks, note, result)
        .local[Context](_.jestClient)
        .transform(FunctionK.lift[Id, Future](Future.successful))
      slackResp <- Slack.sendNotification(esDeployment).local[Context](_.slackCtx)

    } yield {
      Logger.info(s"Created deployment: $esDeployment. Slack response: $slackResp. JIRA response: $jiraResp")
      esDeployment
    }
  }

  def createDeploymentFromKafkaEvent(event: DeploymentKafkaEvent): Kleisli[Future, Context, Deployment] =
    createDeployment(
      event.team,
      event.service,
      event.jiraComponent,
      event.buildId,
      OffsetDateTime.now(),
      event.links.getOrElse(Nil),
      event.note,
      event.result.getOrElse(Succeeded)
    )

  def buildLinks(deployment: Deployment, jiraResponse: Option[WSResponse]) = Kleisli[Future, JIRA.Context, Seq[Link]] {
    ctx =>
      Future.successful(jiraResponse match {
        case None => deployment.links
        case Some(resp) =>
          resp.json.as[JsObject].value.get("key") match {
            case None => deployment.links
            case Some(key) =>
              deployment.links ++ Seq(Link("Jira Ticket", ctx.browseTicketsUrl + key.as[String]))
          }
      })
  }
}
