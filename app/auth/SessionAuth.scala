package auth

import io.flow.session.internal.v0.interfaces.Client
import io.flow.session.internal.v0.models.{OrganizationSession, SessionAuthorizationForm, SessionUndefinedType, ShopifySession}
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
    token: ResolvedToken
  ) (
    implicit ec: ExecutionContext
  ): Future[Option[ResolvedToken]] = {
    token.sessionId match {
      case None => {
        Future.successful(None)
      }

      case Some(session) => {
        sessionClient.sessions.getBySession(
          session = session,
          requestHeaders = flowAuth.headers(token)
        ).flatMap {
          case s: ShopifySession => {
            token.copy(
              organizationId = Some(s),
              environment = Some(orgAuth.environment.toString),
              role = Some(orgAuth.role.toString)
            )
          }
          case s: OrganizationSession => {

          }
          case SessionUndefinedType(other) => {
            Logger.warn(s"[proxy] SessionUndefinedType($other)")
            None
          }
        }.recover {
          case io.flow.session.internal.v0.errors.UnitResponse(404) => None
        }
      }
    }

    sessionFuture.map {

      }
      Some(
        token.copy(
          organizationId = Some(organization),
          environment = Some(orgAuth.environment.toString),
          role = Some(orgAuth.role.toString)
        )
      )
    }.recover {
      case io.flow.organization.v0.errors.UnitResponse(401) => {
        Logger.warn(s"Token[$token] was not authorized for organization[$organization]")
        None
      }

      case io.flow.organization.v0.errors.UnitResponse(404) => {
        Logger.warn(s"Token[$token] organization[$organization] not found")
        None
      }

      case ex: Throwable => {
        sys.error(s"Error communicating with organization server at[${sessionClient.baseUrl}]: ${ex.getMessage}")
      }
    }
  }
}