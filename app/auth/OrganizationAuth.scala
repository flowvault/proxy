package auth

import io.flow.common.v0.models.Environment
import io.flow.organization.v0.interfaces.Client
import io.flow.organization.v0.models.OrganizationAuthorizationForm
import lib.{FlowAuth, ResolvedToken}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

/**
  * Queries organization server to authorize this user for this
  * organization and also pulls the organization's environment.
  */
trait OrganizationAuth {

  def organizationClient: Client
  def flowAuth: FlowAuth

  def authorizeOrganization(
    token: ResolvedToken,
    organization: String
  )(
    implicit ec: ExecutionContext
  ): Future[Option[ResolvedToken]] = {
    val authFuture = (token.environment, token.organizationId) match {

      case (Some(env), Some(_)) => {
        organizationClient.organizationAuthorizations.post(
          OrganizationAuthorizationForm(
            organization = organization,
            environment = Environment(env)
          ),
          requestHeaders = flowAuth.headers(token)
        )
      }

      case (_, _) => {
        organizationClient.organizationAuthorizations.getByOrganization(
          organization = organization,
          requestHeaders = flowAuth.headers(token)
        )
      }
    }

    authFuture.map { orgAuth =>
      Some(
        token.copy(
          organizationId = Some(organization),
          environment = Some(orgAuth.environment.toString),
          role = Some(orgAuth.role.toString)
        )
      )
    }.recover {
      case io.flow.organization.v0.errors.UnitResponse(code) => {
        Logger.warn(s"HTTP $code during token authorization for organization[$organization]")
        None
      }

      case ex: Throwable => {
        sys.error(s"Error communicating with organization server at[${organizationClient.baseUrl}]: ${ex.getMessage}")
      }
    }
  }
}