package controllers

import com.gu.googleauth.GoogleAuthConfig
import io.searchbox.client.JestClient
import play.api.libs.ws.WSClient
import play.api.mvc._

class DeploymentsController(val authConfig: GoogleAuthConfig, val wsClient: WSClient, jestClient: JestClient) extends AuthActions with Controller {

  val healthcheck = Action { Ok("OK") }

  val index = AuthAction { request =>
    implicit val user = request.user
    Ok(views.html.index())
  }

  def search() = AuthAction { request =>
    implicit val user = request.user
    Ok(views.html.search(items = Nil))
  }

}
