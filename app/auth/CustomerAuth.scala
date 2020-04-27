package auth

import io.flow.proxy.auth.v0.models.AuthData
import lib.Constants

import scala.concurrent.{ExecutionContext, Future}

/**
  * Queries session and customer servers to authorize this customer for this
  * organization and also pulls the organization's environment.
  */
trait CustomerAuth extends CustomerAuthHelper with SessionAuthHelper {

  def resolveCustomer(
    requestId: String,
    customerNumber: String,
    sessionId: String
  ) (implicit ec: ExecutionContext): Future[Option[AuthData]] = {
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
  ) (implicit ec: ExecutionContext): Future[Option[AuthData]] = {
    for {
      sessionResolvedTokenOption <- postSessionAuthorization(requestId = requestId, sessionId = sessionId)
      customerResolvedTokenOption <-
        getCustomerResolvedToken(
          requestId = requestId,
          customerNumber = customerNumber,
          sessionResolvedTokenOption = sessionResolvedTokenOption
        )
    } yield {
      customerResolvedTokenOption
    }
  }

}
