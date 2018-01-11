package auth

import io.flow.organization.v0.interfaces.{Client => OrganizationClient}
import io.flow.session.v0.{Client => SessionClient}
import io.flow.session.v0.models._
import lib.{FlowAuth, ResolvedToken}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

/**
  * Queries organization server to authorize this user for this
  * organization and also pulls the organization's environment.
  */
trait SessionAuth {

  def organizationClient: OrganizationClient
  def sessionClient: SessionClient

  def resolveSession(
    requestId: String,
    sessionId: String
  ) (
    implicit ec: ExecutionContext
  ): Future[Option[ResolvedToken]] = {
    sessionClient.sessionAuthorizations.post(
      SessionAuthorizationForm(session = sessionId),
      requestHeaders = FlowAuth.headersFromRequestId(requestId)
    ).map {
      case auth: OrganizationSessionAuthorization => {
        Some(
          ResolvedToken(
            requestId = requestId,
            userId = None,
            environment = Some(auth.environment.toString),
            organizationId = Some(auth.organization.id),
            partnerId = None,
            role = None,
            sessionId = Some(sessionId)
          )
        )
      }

      case SessionAuthorizationUndefinedType(other) => {
        Logger.warn(s"[proxy] session [$sessionId] SessionAuthorizationUndefinedType($other)")
        None
      }
    }.recover {
      case io.flow.organization.v0.errors.UnitResponse(code) => {
        Logger.warn(s"HTTP $code during session [$sessionId] authorization")
        None
      }

      case e: io.flow.session.v0.errors.GenericErrorResponse => {
        Logger.warn(s"[proxy] 422 authorizing session [$sessionId]: ${e.genericError.messages.mkString(", ")}")
        None
      }

      case ex: Throwable => {
        val msg = s"Error communication with session service for session [$sessionId]: ${ex.getMessage}"
        Logger.error(msg, ex)
        throw new RuntimeException(msg, ex)
      }
    }
  }
}