package controllers

import com.gu.googleauth.GoogleAuthConfig
import es.ES
import io.searchbox.client.JestClient
import play.api.libs.ws.WSClient
import play.api.mvc._

class ApiKeysController(val authConfig: GoogleAuthConfig, val wsClient: WSClient, jestClient: JestClient) extends AuthActions with Controller {

  val healthcheck = Action { Ok("OK") }

  val index = AuthAction { request =>
    implicit val user = request.user
    Ok(views.html.index())
  }

  def list(offset: Int = 0) = AuthAction { request =>
    implicit val user = request.user
    val items = ES.ApiKeys.list(offset).run(jestClient)
    Ok(views.html.apikeys.list(items))
  }

}
