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

  def disable(keyId: String) = AuthAction { request =>
    implicit val user = request.user
    ES.ApiKeys.disable(keyId).run(jestClient)
    Redirect(routes.ApiKeysController.list(0)).flashing("info" -> "Disabled API key")
  }

  def enable(keyId: String) = AuthAction { request =>
    implicit val user = request.user
    ES.ApiKeys.enable(keyId).run(jestClient)
    Redirect(routes.ApiKeysController.list(0)).flashing("info" -> "Enabled API key")
  }

  def delete(keyId: String) = AuthAction { request =>
    implicit val user = request.user
    ES.ApiKeys.delete(keyId).run(jestClient)
    Redirect(routes.ApiKeysController.list(0)).flashing("info" -> "Deleted API key")
  }

}
