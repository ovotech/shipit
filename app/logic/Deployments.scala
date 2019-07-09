package logic

import java.time.OffsetDateTime

import cats.Id
import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.syntax.option._
import cats.instances.future._
import com.gu.googleauth.UserIdentity
import datadog.Datadog
import es.ES
import io.searchbox.client.JestClient
import models._
import play.api.Logger
import play.api.libs.ws.WSResponse
import slack.Slack

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Deployments {

  case class Context(jestClient: JestClient,
                     slackCtx: Slack.Context,
                     datadogCtx: Datadog.Context,
                     isAdmin: UserIdentity => Boolean)

  def createDeployment(team: String,
                       service: String,
                       buildId: String,
                       timestamp: OffsetDateTime,
                       links: Seq[Link],
                       note: Option[String],
                       notifySlackChannel: Option[String]): Kleisli[Future, Context, Deployment] = {

    val deployment = Deployment(team, service, buildId, timestamp, links, note)

    for {
      _               <- persistToES(deployment)
      slackResp       <- sendMainSlackNotification(deployment)
      secondSlackResp <- sendSlackNotificationToCustomChannel(deployment, notifySlackChannel)
      datadogResp     <- sendEventToDatadog(deployment)
    } yield {
      Logger.info(
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
    ES.Deployments
      .create(deployment)
      .local[Context](_.jestClient)
      .mapK(FunctionK.lift[Id, Future](Future.successful))

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

  private def sendEventToDatadog(deployment: Deployment): Kleisli[Future, Context, WSResponse] =
    Datadog.sendEvent(deployment).local[Context](_.datadogCtx)

}
