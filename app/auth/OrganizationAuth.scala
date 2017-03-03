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

  def resolveOrganization(
    token: ResolvedToken,
    organization: String
  ) (
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
      case io.flow.organization.v0.errors.UnitResponse(401) => {
        Logger.warn(s"Token[$token] was not authorized for organization[$organization]")
        None
      }

      case io.flow.organization.v0.errors.UnitResponse(404) => {
        Logger.warn(s"Token[$token] organization[$organization] not found")
        None
      }

      case ex: Throwable => {
        sys.error(s"Error communicating with organization server at[${organizationClient.baseUrl}]: ${ex.getMessage}")
      }
    }
  }
}