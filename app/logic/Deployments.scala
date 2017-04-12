package logic

import java.time.OffsetDateTime

import cats.Id
import cats.data.Kleisli
import cats.arrow.FunctionK
import cats.instances.future._
import com.gu.googleauth.UserIdentity

import scala.concurrent.ExecutionContext.Implicits.global
import es.ES
import io.searchbox.client.JestClient
import jira.JIRA
import models.DeploymentResult.Succeeded
import models._
import play.api.Logger
import slack.Slack

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
                       result: DeploymentResult): Kleisli[Future, Context, Deployment] =
    for {
      deployment <- ES.Deployments
        .create(team, service, jiraComponent, buildId, timestamp, links, note, result)
        .local[Context](_.jestClient)
        .transform(FunctionK.lift[Id, Future](Future.successful))
      slackResp <- Slack.sendNotification(deployment).local[Context](_.slackCtx)
      jiraResp  <- JIRA.createIssueIfPossible(deployment).local[Context](_.jiraCtx)
    } yield {
      Logger.info(s"Created deployment: $deployment. Slack response: $slackResp. JIRA response: $jiraResp")
      deployment
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
}
