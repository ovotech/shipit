package logic

import java.time.OffsetDateTime

import cats.data.Kleisli
import cats.instances.future._
import cats.syntax.option._
import com.gu.googleauth.UserIdentity
import datadog.Datadog
import deployments.{Deployment, Deployments => Depls, Link}
import models._
import org.slf4j.LoggerFactory
import play.api.libs.ws.WSResponse
import slack.Slack

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Deployments {

  private val logger = LoggerFactory.getLogger(getClass)

  case class Context(
      slackCtx: Slack.Context,
      datadogCtx: Datadog.Context,
      deployments: Depls[Future],
      isAdmin: UserIdentity => Boolean
  )

  def createDeployment(
      team: String,
      service: String,
      buildId: String,
      timestamp: OffsetDateTime,
      links: Seq[Link],
      note: Option[String],
      notifySlackChannel: Option[String]
  ): Kleisli[Future, Context, Deployment] = {

    val deployment = Deployment(team, service, buildId, timestamp, links.toList, note)

    for {
      _               <- persistToES(deployment)
      slackResp       <- sendMainSlackNotification(deployment)
      secondSlackResp <- sendSlackNotificationToCustomChannel(deployment, notifySlackChannel)
      datadogResp     <- sendEventToDatadog(deployment)
    } yield {
      logger.info(
        s"""
           |Created deployment: $deployment.
           |- First Slack response: $slackResp.
           |- Second Slack response: $secondSlackResp.
           |- Datadog response: $datadogResp
         """.stripMargin
      )
      deployment
    }
  }

  private def persistToES(deployment: Deployment): Kleisli[Future, Context, Identified[Deployment]] =
    Kleisli(ctx => ctx.deployments.create(deployment))

  private def sendMainSlackNotification(deployment: Deployment): Kleisli[Future, Context, WSResponse] =
    Slack.sendNotification(deployment, channel = None).local[Context](_.slackCtx)

  private def sendSlackNotificationToCustomChannel(
      deployment: Deployment,
      channel: Option[String]
  ): Kleisli[Future, Context, Option[WSResponse]] = {
    channel match {
      case Some(ch) => Slack.sendNotification(deployment, channel = Some(ch)).local[Context](_.slackCtx).map(_.some)
      case None     => Kleisli.pure[Future, Context, Option[WSResponse]](None)
    }
  }

  private def sendEventToDatadog(deployment: Deployment): Kleisli[Future, Context, WSResponse] =
    Datadog.sendEvent(deployment).local[Context](_.datadogCtx)
}
