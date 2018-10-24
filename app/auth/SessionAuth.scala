package auth

import io.flow.log.RollbarLogger
import io.flow.organization.v0.interfaces.{Client => OrganizationClient}
import io.flow.session.v0.{Client => SessionClient}
import io.flow.session.v0.models._
import lib.{FlowAuth, ResolvedToken}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Queries organization server to authorize this user for this
  * organization and also pulls the organization's environment.
  */
trait SessionAuth {

  def organizationClient: OrganizationClient
  def sessionClient: SessionClient
  def logger: RollbarLogger

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
        logger.
          requestId(requestId).
          withKeyValue("session_id", sessionId).
          withKeyValue("type", other).
          warn("SessionAuthorizationUndefinedType")
        None
      }
    }.recover {
      case io.flow.organization.v0.errors.UnitResponse(code) => {
        logger.
          requestId(requestId).
          withKeyValue("http_status_code", code).
          withKeyValue("session_id", sessionId).
          warn("Unexpected HTTP Status Code - request will not be authorized")
        None
      }

      case e: io.flow.session.v0.errors.GenericErrorResponse => {
        logger.
          requestId(requestId).
          withKeyValue("http_status_code", "422").
          withKeyValue("session_id", sessionId).
          withKeyValue("messages", e.genericError.messages.mkString(", ")).
          warn("422 authorizing session")
        None
      }

      case ex: Throwable => {
        val msg = "Error communication with session service"
        logger.
          requestId(requestId).
          withKeyValue("session_id", sessionId).
          error(msg, ex)
        throw new RuntimeException(msg, ex)
      }
    }
  }
}