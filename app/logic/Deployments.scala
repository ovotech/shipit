package logic

import java.time.OffsetDateTime

import cats.Id
import cats.data.Kleisli
import cats.arrow.FunctionK
import cats.instances.future._

import scala.concurrent.ExecutionContext.Implicits.global
import es.ES
import io.searchbox.client.JestClient
import models.DeploymentResult.Succeeded
import models.{Deployment, DeploymentKafkaEvent, DeploymentResult, Link}
import play.api.Logger
import slack.Slack

import scala.concurrent.Future

object Deployments {

  case class Context(jestClient: JestClient, slackCtx: Slack.Context)

  def createDeployment(team: String,
                       service: String,
                       buildId: String,
                       timestamp: OffsetDateTime,
                       links: Seq[Link],
                       note: Option[String],
                       result: DeploymentResult): Kleisli[Future, Context, Deployment] =
    for {
      deployment <- ES.Deployments
        .create(team, service, buildId, timestamp, links, note, result)
        .local[Context](_.jestClient)
        .transform(FunctionK.lift[Id, Future](Future.successful))
      slackResp <- Slack.sendNotification(deployment).local[Context](_.slackCtx)
    } yield {
      Logger.info(s"Created deployment: $deployment")
      deployment
    }

  def createDeploymentFromKafkaEvent(event: DeploymentKafkaEvent): Kleisli[Future, Context, Deployment] =
    createDeployment(
      event.team,
      event.service,
      event.buildId,
      OffsetDateTime.now(),
      event.links.getOrElse(Nil),
      event.note,
      event.result.getOrElse(Succeeded)
    )
}
