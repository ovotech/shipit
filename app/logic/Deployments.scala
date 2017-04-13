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

    val deployment = Deployment(team, service, jiraComponent, buildId, timestamp, links, note, result)

    for {
      jiraResp           <- JIRA.createIssueIfPossible(deployment).local[Context](_.jiraCtx)
      enrichedDeployment <- enrichWithJiraInfo(deployment, jiraResp).local[Context](_.jiraCtx)
      identifiedDeployment <- ES.Deployments
        .create(enrichedDeployment)
        .local[Context](_.jestClient)
        .transform(FunctionK.lift[Id, Future](Future.successful))
      slackResp <- Slack.sendNotification(enrichedDeployment).local[Context](_.slackCtx)

    } yield {
      Logger.info(s"Created deployment: $enrichedDeployment. Slack response: $slackResp. JIRA response: $jiraResp")
      enrichedDeployment
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

  private def enrichWithJiraInfo(deployment: Deployment, jiraResponse: Option[WSResponse]) =
    Kleisli[Future, JIRA.Context, Deployment] { ctx =>
      Future.successful(jiraResponse match {
        case None => deployment
        case Some(resp) =>
          resp.json.as[JsObject].value.get("key") match {
            case None => deployment
            case Some(key) =>
              Deployment(
                deployment.team,
                deployment.service,
                deployment.jiraComponent,
                deployment.buildId,
                deployment.timestamp,
                deployment.links ++ Seq(Link("Jira Ticket", ctx.browseTicketsUrl + key.as[String])),
                deployment.note,
                deployment.result
              )
          }
      })
    }
}
