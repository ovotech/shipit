package jira

import cats.data.Kleisli
import cats.instances.future._
import cats.syntax.option._
import jira.JIRA.{Context, CreateIssueKey}
import play.Logger
import play.api.libs.json.Json.obj
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.libs.ws.{WSAuthScheme, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object JiraTransitions {

  case class Transition(id: String, name: String)
  case class Transitions(transitions: Set[Transition])

  implicit val transitionReads  = Json.reads[Transition]
  implicit val transitionsReads = Json.reads[Transitions]

  def transition(issueKeyOpt: Option[CreateIssueKey], transitionName: String) = {

    issueKeyOpt match {
      case Some(issueKey) =>
        getTransitionId(issueKey.key, transitionName).flatMap {
          case Some(transitionId) => performTransition(issueKey.key, transitionId)
          case None =>
            Logger.error(s"No transition ID found for name $transitionName")
            Kleisli.pure[Future, Context, Option[WSResponse]](None)
        }
      case None => Kleisli.pure[Future, Context, Option[WSResponse]](None)
    }
  }

  private def getTransitionId(issueKey: String, transitionName: String) = Kleisli[Future, Context, Option[String]] {
    ctx =>
      ctx.wsClient
        .url(s"${ctx.issueApiUrl}/$issueKey/transitions")
        .withAuth(ctx.username, ctx.password, WSAuthScheme.BASIC)
        .withHeaders("Content-Type" -> "application/json")
        .get()
        .map(response =>
          response.json.validate[Transitions] match {
            case JsSuccess(transitions, _) => transitions.transitions.find(t => t.name == transitionName).map(_.id)
            case JsError(errors) =>
              Logger.error(s"Failed to deserialize jira transitions: $errors")
              None
        })
  }

  private def performTransition(issueKey: String, transitionId: String) =
    Kleisli[Future, Context, Option[WSResponse]] { ctx =>
      val payload = buildTransitionPayload(transitionId)

      ctx.wsClient
        .url(s"${ctx.issueApiUrl}/$issueKey/transitions")
        .withAuth(ctx.username, ctx.password, WSAuthScheme.BASIC)
        .withHeaders("Content-Type" -> "application/json")
        .post(payload)
        .map(_.some)
    }

  private def buildTransitionPayload(transitionId: String): JsValue =
    obj(
      "transition" -> obj(
        "id" -> transitionId
      ),
      "comment" -> List(
        obj(
          "add" -> obj(
            "body" -> "Transitioned by :shipit:"
          )
        ))
    )

}
