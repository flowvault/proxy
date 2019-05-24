package auth

import lib.{Constants, ResolvedToken}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Queries session server to authorize this user for this
  * organization and also pulls the organization's environment.
  */
trait SessionAuth extends SessionAuthHelper {

  def resolveSession(
    requestId: String,
    sessionId: String
  ) (implicit ec: ExecutionContext): Future[Option[ResolvedToken]] = {
    if (Constants.StopWords.contains(sessionId)) {
      // javascript sending in 'undefined' or 'null' as session id
      Future.successful(None)
    } else {
      doResolveSession(
        requestId = requestId,
        sessionId = sessionId
      )
    }
  }

  private[this] def doResolveSession(
    requestId: String,
    sessionId: String
  ) (
    implicit ec: ExecutionContext
  ): Future[Option[ResolvedToken]] = postSessionAuthorization(requestId = requestId, sessionId = sessionId)
}
