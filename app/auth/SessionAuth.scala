package auth

import io.flow.common.v0.models.Role
import io.flow.session.internal.v0.interfaces.Client
import io.flow.session.internal.v0.models._
import lib.{FlowAuth, ResolvedToken}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

/**
  * Queries organization server to authorize this user for this
  * organization and also pulls the organization's environment.
  */
trait SessionAuth {

  def sessionClient: Client
  def flowAuth: FlowAuth

  def resolveSession(
    sessionId: String,
    requestHeaders: Seq[(String, String)]
  ) (
    implicit ec: ExecutionContext
  ): Future[Option[ResolvedToken]] = {
    sessionClient.sessionAuthorizations.post(
      SessionAuthorizationForm(session = sessionId),
      requestHeaders = requestHeaders
    ).map {
      case auth: OrganizationSessionAuthorization => {
        println(s"SESSION AUTH: $auth")
        // TODO
        None
      }

      case SessionAuthorizationUndefinedType(other) => {
        Logger.warn(s"[proxy] SessionAuthorizationUndefinedType($other)")
        None
      }
    }.recover {
      case io.flow.organization.v0.errors.UnitResponse(code) => {
        Logger.warn(s"HTTP $code during session authorization")
        None
      }

      case e: io.flow.session.internal.v0.errors.GenericErrorResponse => {
        Logger.warn(s"[proxy] 422 authorizing session: ${e.genericError.messages.mkString(", ")}")
        None
      }
    }
  }
}