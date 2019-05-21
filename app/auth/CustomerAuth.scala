package auth

import io.flow.session.v0.models._
import io.flow.session.v0.{Client => SessionClient}
import lib.{Constants, FlowAuth, ResolvedToken}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Queries session server to authorize this user for this
  * organization and also pulls the organization's environment.
  */
trait CustomerAuth extends LoggingHelper {

  def sessionClient: SessionClient

  def resolveCustomer(
    requestId: String,
    customerNumber: String,
    sessionId: String
  ) (
    implicit ec: ExecutionContext
  ): Future[Option[ResolvedToken]] = {
    if (Constants.StopWords.contains(sessionId)) {
      // javascript sending in 'undefined' or 'null' as session id
      Future.successful(None)
    } else {
      doResolveCustomer(
        requestId = requestId,
        customerNumber = customerNumber,
        sessionId = sessionId
      )
    }
  }

  private[this] def doResolveCustomer(
    requestId: String,
    customerNumber: String,
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
            environment = Some(auth.environment),
            organizationId = Some(auth.organization.id),
            partnerId = None,
            role = None,
            customerNumber = Some(customerNumber),
            sessionId = Some(sessionId)
          )
        )
      }

      case SessionAuthorizationUndefinedType(other) => {
        log(requestId).
          withKeyValue("session_id", sessionId).
          withKeyValue("type", other).
          warn("SessionAuthorizationUndefinedType")
        None
      }
    }.recover {
      case io.flow.organization.v0.errors.UnitResponse(code) => {
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