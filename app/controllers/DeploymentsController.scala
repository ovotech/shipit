package controllers

import java.time.OffsetDateTime

import com.gu.googleauth.GoogleAuthConfig
import es.ES
import logic.Deployments
import models.DeploymentResult.Succeeded
import models.{DeploymentResult, Link}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class DeploymentsController(val authConfig: GoogleAuthConfig, val wsClient: WSClient, ctx: Deployments.Context)
    extends AuthActions
    with ApiKeyAuth
    with Controller {

  import DeploymentsController._

  val jestClient = ctx.jestClient

  val healthcheck = Action { Ok("OK") }

  val index = AuthAction { request =>
    implicit val user = request.user
    Ok(views.html.index())
  }

  def search(team: Option[String],
             service: Option[String],
             buildId: Option[String],
             result: Option[String],
             page: Int) = AuthAction { implicit request =>
    implicit val user   = request.user
    val showAdminColumn = ctx.isAdmin(user)
    val (teamQuery, serviceQuery, buildIdQuery, resultQuery) =
      (team.filter(_.nonEmpty),
       service.filter(_.nonEmpty),
       buildId.filter(_.nonEmpty),
       result.flatMap(DeploymentResult.fromLowerCaseString))
    val searchResult = ES.Deployments.search(teamQuery, serviceQuery, buildIdQuery, resultQuery, page).run(jestClient)
    Ok(
      views.html.deployments.search(searchResult, teamQuery, serviceQuery, buildIdQuery, resultQuery, showAdminColumn))
  }

  def create = ApiKeyAuthAction.async { implicit request =>
    DeploymentForm.bindFromRequest.fold(
      _ =>
        Future.successful(
          BadRequest(
            """You must include at least the following form fields in your POST: 'team', 'service', 'buildId'.
            |You may also include the following fields:
            |- one or more links (e.g. links[0][title]=PR, links[0][url]=http://github.com/my-pr)
            |- a 'jiraComponent' field (only needed if you want shipit to create a JIRA release ticket for the deployment)
            |- a 'note' field containing any notes about the deployment
            |- a 'result' field containing the result of the deployment ('succeeded', 'failed' or 'cancelled').
            |""".stripMargin
          )
      ),
      data => {
        Deployments
          .createDeployment(
            data.team,
            data.service,
            data.jiraComponent,
            data.buildId,
            OffsetDateTime.now(),
            data.links.getOrElse(Nil),
            data.note,
            data.result.getOrElse(Succeeded)
          )
          .run(ctx)
          .map(_ => Ok("ok"))
      }
    )
  }

  def delete(id: String) = AuthAction { request =>
    implicit val user = request.user
    if (ctx.isAdmin(user)) {
      ES.Deployments.delete(id).run(ctx.jestClient) match {
        case Left(errorMessage) => Ok(s"Failed to delete $id. Error message: $errorMessage")
        case Right(_)           => Ok(s"Deleted $id")
      }
    } else
      Forbidden("Sorry, you're not cool enough")
  }

}

object DeploymentsController {

  case class DeploymentFormData(
      team: String,
      service: String,
      jiraComponent: Option[String],
      buildId: String,
      links: Option[List[Link]],
      note: Option[String],
      result: Option[Product with Serializable with DeploymentResult]
  )

  private val deploymentResult = nonEmptyText
    .verifying(DeploymentResult.fromLowerCaseString(_).isDefined)
    .transform(DeploymentResult.fromLowerCaseString(_).get, DeploymentResult.toLowerCaseString)

  val DeploymentForm = Form(
    mapping(
      "team"          -> nonEmptyText,
      "service"       -> nonEmptyText,
      "jiraComponent" -> optional(text),
      "buildId"       -> nonEmptyText,
      "links" -> optional(
        list(
          mapping(
            "title" -> nonEmptyText,
            "url"   -> nonEmptyText
          )(Link.apply)(Link.unapply))),
      "note"   -> optional(text),
      "result" -> optional(deploymentResult)
    )(DeploymentFormData.apply)(DeploymentFormData.unapply))

}
