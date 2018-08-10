package controllers

import com.gu.googleauth.{AuthAction, GoogleAuthConfig, UserIdentity}
import play.api.libs.ws.WSClient
import play.api.mvc._

class MainController(controllerComponents: ControllerComponents,
                     authAction: AuthAction[AnyContent],
                     val authConfig: GoogleAuthConfig,
                     val wsClient: WSClient)
    extends AbstractController(controllerComponents) {

  val healthcheck = Action { request =>
    println(s"I'm healthy! ${request.remoteAddress}")
    Ok("OK")
  }

  val index = authAction { request =>
    implicit val user: UserIdentity = request.user
    Ok(views.html.index())
  }

  val guide = authAction { request =>
    implicit val user: UserIdentity = request.user
    Ok(views.html.guide())
  }

}
