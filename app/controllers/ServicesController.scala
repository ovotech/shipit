package controllers

import com.gu.googleauth.{AuthAction, GoogleAuthConfig, UserIdentity}
import elasticsearch.Elastic55
import io.searchbox.client.JestClient
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext

class ServicesController(
    controllerComponents: ControllerComponents,
    authAction: AuthAction[AnyContent],
    val authConfig: GoogleAuthConfig,
    val wsClient: WSClient,
    jestClient: JestClient
)(implicit val ec: ExecutionContext)
    extends AbstractController(controllerComponents) {

  def list(days: Int) = authAction { request =>
    implicit val user: UserIdentity = request.user
    val services                    = Elastic55.Deployments.listServices(deployedInLastNDays = days).run(jestClient)
    Ok(views.html.services.list(services, days))
  }

}
