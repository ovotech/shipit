package logic

import java.time.OffsetDateTime

import cats.Id
import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.syntax.option._
import cats.instances.future._
import com.gu.googleauth.UserIdentity
import es.ES
import io.searchbox.client.JestClient
import jira.JIRA
import jira.JIRA.CreateIssueKey
import models._
import play.api.Logger
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
                       notifySlackChannel: Option[String]): Kleisli[Future, Context, Deployment] = {

    val deployment = Deployment(team, service, jiraComponent, buildId, timestamp, links, note)

    for {
      jiraResp           <- JIRA.createAndTransitionIssueIfPossible(deployment).local[Context](_.jiraCtx)
      enrichedDeployment <- enrichWithJiraInfo(deployment, jiraResp)
      _                  <- persistToES(enrichedDeployment)
      slackResp          <- sendMainSlackNotification(enrichedDeployment)
      secondSlackResp    <- sendSlackNotificationToCustomChannel(enrichedDeployment, notifySlackChannel)
    } yield {
      Logger.info(
        s"""
           |Created deployment: $enrichedDeployment. First Slack response: $slackResp. Second Slack response: $secondSlackResp. JIRA response: $jiraResp
         """.stripMargin
      )
      enrichedDeployment
    }
  }

  private def persistToES(deployment: Deployment): Kleisli[Future, Context, Identified[Deployment]] =
    ES.Deployments
      .create(deployment)
      .local[Context](_.jestClient)
      .mapK(FunctionK.lift[Id, Future](Future.successful))

  private def enrichWithJiraInfo(deployment: Deployment, issueKeyOpt: Option[CreateIssueKey]) =
    Kleisli[Future, JIRA.Context, Deployment] { ctx =>
      Future.successful(issueKeyOpt match {
        case None => deployment
        case Some(issueKey) =>
          deployment.copy(links = deployment.links :+ Link("Jira Ticket", ctx.browseTicketsUrl + issueKey.key))
      })
    }.local[Context](_.jiraCtx)

  private def sendMainSlackNotification(deployment: Deployment): Kleisli[Future, Context, WSResponse] =
    Slack.sendNotification(deployment, channel = None).local[Context](_.slackCtx)

  private def sendSlackNotificationToCustomChannel(
      deployment: Deployment,
      channel: Option[String]): Kleisli[Future, Context, Option[WSResponse]] = {
    channel match {
      case Some(ch) => Slack.sendNotification(deployment, channel = Some(ch)).local[Context](_.slackCtx).map(_.some)
      case None     => Kleisli.pure[Future, Context, Option[WSResponse]](None)
    }
  }

}
