package controllers

import com.gu.googleauth.GoogleAuthConfig
import play.api.libs.ws.WSClient
import play.api.mvc._

class AuthController(val authConfig: GoogleAuthConfig, val wsClient: WSClient) extends AuthActions with Controller {

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
