package controllers

import java.util.UUID

import com.gu.googleauth.GoogleAuthConfig
import es.ES
import io.searchbox.client.JestClient
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

class ApiKeysController(val authConfig: GoogleAuthConfig, val wsClient: WSClient, jestClient: JestClient) extends AuthActions with Controller {
  import ApiKeysController._

  def list(offset: Int = 0) = AuthAction { implicit request =>
    implicit val user = request.user
    val items = ES.ApiKeys.list(offset).run(jestClient)
    Ok(views.html.apikeys.list(items))
  }

  def create = AuthAction { implicit request =>
    implicit val user = request.user
    CreateKeyForm.bindFromRequest.fold(
      _ => Redirect(routes.ApiKeysController.list(0)).flashing("error" -> "Invalid request"),
      data => {
        val apiKey = ES.ApiKeys.create(
          key = UUID.randomUUID().toString,
          description = data.description,
          createdBy = user.fullName
        ).run(jestClient)
        Redirect(routes.ApiKeysController.list(0)).flashing("info" -> s"Created API key: ${apiKey.key}")
      }
    )
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

object ApiKeysController {

  case class CreateKeyFormData(description: Option[String])

  val CreateKeyForm = Form(mapping(
    "description" -> optional(text)
  )(CreateKeyFormData.apply)(CreateKeyFormData.unapply))

}
