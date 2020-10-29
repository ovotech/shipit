package controllers

import com.gu.googleauth.{AuthAction, GoogleAuthConfig, UserIdentity}
import deployments.Deployments
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class ServicesController(
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    val authConfig: GoogleAuthConfig,
    val wsClient: WSClient,
    deployments: Deployments[Future]
)(implicit val ec: ExecutionContext)
    extends AbstractController(controllerComponents) {

  def list(days: Int): Action[AnyContent] =
    authAction.async { request =>
      implicit val user: UserIdentity = request.user
      deployments.recent(deployedInLastNDays = days).map(s => Ok(views.html.services.list(s, days)))
    }
}
