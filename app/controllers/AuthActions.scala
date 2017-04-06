package controllers

import com.gu.googleauth.{Actions, UserIdentity}
import play.api.Logger
import play.api.mvc.ActionBuilder
import play.api.mvc.Security.{AuthenticatedBuilder, AuthenticatedRequest}

trait AuthActions extends Actions {
  override val loginTarget           = routes.AuthController.login()
  override val defaultRedirectTarget = routes.MainController.index()
  override val failureRedirectTarget = routes.AuthController.authError()
}
