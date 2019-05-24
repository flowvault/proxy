package auth

import lib.{Constants, ResolvedToken}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Queries session server to authorize this user for this
  * organization and also pulls the organization's environment.
  */
trait CustomerAuth extends SessionAuthHelper {

  def resolveCustomer(
    requestId: String,
    customerNumber: String,
    sessionId: String
  ) (
    implicit ec: ExecutionContext
  ): Future[Option[ResolvedToken]] = {
    if (Constants.StopWords.contains(sessionId) || Constants.StopWords.contains(customerNumber)) {
      // javascript sending in 'undefined' or 'null' as session id or customer number
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
    postSessionAuthorization(requestId = requestId, sessionId = sessionId) { resolvedToken =>
      resolvedToken.copy(
        customerNumber = Some(customerNumber)
      )
    }
  }
}
