package controllers

import java.util.UUID

import com.gu.googleauth.{AuthAction, GoogleAuthConfig, UserIdentity}
import es.ES
import io.searchbox.client.JestClient
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

class ApiKeysController(controllerComponents: ControllerComponents,
                        authAction: AuthAction[AnyContent],
                        val authConfig: GoogleAuthConfig,
                        val wsClient: WSClient,
                        jestClient: JestClient)
    extends AbstractController(controllerComponents) {
  import ApiKeysController._

  def list(page: Int) = authAction { implicit request =>
    implicit val user: UserIdentity = request.user
    val keys                        = ES.ApiKeys.list(page).run(jestClient)
    Ok(views.html.apikeys.list(keys))
  }

  def create = authAction { implicit request =>
    implicit val user: UserIdentity = request.user
    CreateKeyForm.bindFromRequest.fold(
      _ => Redirect(routes.ApiKeysController.list()).flashing("error" -> "Invalid request"),
      data => {
        val apiKey = ES.ApiKeys
          .create(
            key = UUID.randomUUID().toString,
            description = data.description,
            createdBy = user.email
          )
          .run(jestClient)
        Redirect(routes.ApiKeysController.list()).flashing("info" -> s"Created API key: ${apiKey.key}")
      }
    )
  }

  def disable(keyId: String) = authAction { request =>
    implicit val user: UserIdentity = request.user
    ES.ApiKeys.disable(keyId).run(jestClient)
    Redirect(routes.ApiKeysController.list()).flashing("info" -> "Disabled API key")
  }

  def enable(keyId: String) = authAction { request =>
    implicit val user: UserIdentity = request.user
    ES.ApiKeys.enable(keyId).run(jestClient)
    Redirect(routes.ApiKeysController.list()).flashing("info" -> "Enabled API key")
  }

  def delete(keyId: String) = authAction { request =>
    implicit val user: UserIdentity = request.user
    val succeeded                   = ES.ApiKeys.delete(keyId).run(jestClient)
    if (succeeded)
      Redirect(routes.ApiKeysController.list()).flashing("info" -> "Deleted API key")
    else
      Redirect(routes.ApiKeysController.list()).flashing("error" -> "Failed to delete API key")
  }

}

object ApiKeysController {

  case class CreateKeyFormData(description: Option[String])

  val CreateKeyForm = Form(
    mapping(
      "description" -> optional(text)
    )(CreateKeyFormData.apply)(CreateKeyFormData.unapply))

}
