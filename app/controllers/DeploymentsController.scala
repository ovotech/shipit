package controllers

import java.time.OffsetDateTime

import com.gu.googleauth.GoogleAuthConfig
import es.ES
import io.searchbox.client.JestClient
import models.DeploymentResult.Succeeded
import models.{DeploymentResult, Link}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.ws.WSClient
import play.api.mvc._

class DeploymentsController(val authConfig: GoogleAuthConfig, val wsClient: WSClient, val jestClient: JestClient)
  extends AuthActions
  with ApiKeyAuth
  with Controller {

  import DeploymentsController._

  val healthcheck = Action { Ok("OK") }

  val index = AuthAction { request =>
    implicit val user = request.user
    Ok(views.html.index())
  }

  def search(team: Option[String], service: Option[String], buildId: Option[String]) = AuthAction { request =>
    implicit val user = request.user
    val items = ES.Deployments.search(offset = 0).run(jestClient)
    Ok(views.html.deployments.search(items, team, service, buildId, Some(DeploymentResult.Failed)))
  }

  def create = ApiKeyAuthAction { implicit request =>
    DeploymentForm.bindFromRequest.fold(_ => BadRequest, data => {
      ES.Deployments.create(
        data.team,
        data.service,
        data.buildId,
        OffsetDateTime.now(),
        data.links.getOrElse(Nil),
        data.result.getOrElse(Succeeded)
      ).run(jestClient)
      Ok("ok")
    })
  }

}

object DeploymentsController {

  case class DeploymentFormData(
    team: String,
    service: String,
    buildId: String,
    links: Option[List[Link]],
    result: Option[Product with Serializable with DeploymentResult]
  )

  private val deploymentResult = nonEmptyText
    .verifying(DeploymentResult.fromString(_).isDefined)
    .transform(DeploymentResult.fromString(_).get, DeploymentResult.toString)

  val DeploymentForm = Form(mapping(
    "team" -> nonEmptyText,
    "service" -> nonEmptyText,
    "buildId" -> nonEmptyText,
    "links" -> optional(list(mapping(
      "title" -> nonEmptyText,
      "url" -> nonEmptyText
    )(Link.apply)(Link.unapply))),
    "result" -> optional(deploymentResult)
  )(DeploymentFormData.apply)(DeploymentFormData.unapply))

}
