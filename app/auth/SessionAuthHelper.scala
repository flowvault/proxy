package auth

import io.flow.session.v0.models.{OrganizationSessionAuthorization, SessionAuthorizationForm, SessionAuthorizationUndefinedType}
import io.flow.session.v0.{Client => SessionClient}
import lib.{FlowAuth, ResolvedToken}

import scala.concurrent.{ExecutionContext, Future}

trait SessionAuthHelper extends LoggingHelper {

  def sessionClient: SessionClient

  private[auth] def postSessionAuthorization(
    requestId: String,
    sessionId: String
  )(implicit ec: ExecutionContext): Future[Option[ResolvedToken]] = {
    sessionClient.sessionAuthorizations.post(
      SessionAuthorizationForm(session = sessionId),
      requestHeaders = FlowAuth.headersFromRequestId(requestId)
    ).map {
      case auth: OrganizationSessionAuthorization => {
        val sessionResolvedToken =
          ResolvedToken(
            requestId = requestId,
            userId = None,
            environment = Some(auth.environment),
            organizationId = Some(auth.organization.id),
            partnerId = None,
            role = None,
            sessionId = Some(sessionId)
          )

        Some(sessionResolvedToken)
      }

      case SessionAuthorizationUndefinedType(other) => {
        log(requestId).
          withKeyValue("session_id", sessionId).
          withKeyValue("type", other).
          warn("SessionAuthorizationUndefinedType")
        None
      }
    }.recover {
      case io.flow.session.v0.errors.UnitResponse(code) => {
        log(requestId).
          withKeyValue("http_status_code", code).
          withKeyValue("session_id", sessionId).
          warn("Unexpected HTTP Status Code - request will not be authorized")
        None
      }

      case e: io.flow.session.v0.errors.GenericErrorResponse => {
        e.genericError.messages.mkString(", ") match {
          case "Session does not exist" => // expected - don't log
          case _ => {
            log(requestId).
              withKeyValue("http_status_code", "422").
              withKeyValue("session_id", sessionId).
              withKeyValues("message", e.genericError.messages).
              warn("422 authorizing session")
          }
        }

        None
      }

      case ex: Throwable => {
        val msg = "Error communication with session service"
        log(requestId).
          withKeyValue("session_id", sessionId).
          error(msg, ex)
        throw new RuntimeException(msg, ex)
      }
    }
  }

}
