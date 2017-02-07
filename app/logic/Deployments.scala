package logic

import java.time.OffsetDateTime

import cats.Id
import cats.data.Kleisli
import cats.arrow.FunctionK
import cats.instances.future._

import scala.concurrent.ExecutionContext.Implicits.global
import es.ES
import io.searchbox.client.JestClient
import models.{Deployment, DeploymentResult, Link}
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
               result: DeploymentResult): Kleisli[Future, Context, Deployment] =
    for {
      deployment <- ES.Deployments.create(team, service, buildId, timestamp, links, result)
                      .local[Context](_.jestClient)
                      .transform(FunctionK.lift[Id, Future](Future.successful))
      slackResp <- Slack.sendNotification(deployment).local[Context](_.slackCtx)
    } yield deployment
}
