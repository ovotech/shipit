package controllers

import com.gu.googleauth.{GoogleAuthConfig, LoginSupport}
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext

class AuthController(
    controllerComponents: ControllerComponents,
    val authConfig: GoogleAuthConfig,
    val wsClient: WSClient
)(implicit ec: ExecutionContext)
    extends AbstractController(controllerComponents)
    with LoginSupport {

  override val defaultRedirectTarget = routes.MainController.index()
  override val failureRedirectTarget = routes.AuthController.authError()

  def login = Action.async { implicit request =>
    startGoogleLogin()
  }

  def oauth2Callback = Action.async { implicit request =>
    processOauth2Callback()
  }

  def authError = Action { request =>
    val error = request.flash.get("error")
    Ok(views.html.authError(error))
  }

}
