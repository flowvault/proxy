package auth

import io.flow.organization.v0.interfaces.Client
import io.flow.organization.v0.models.OrganizationAuthorizationForm
import lib.{Constants, FlowAuth, ResolvedToken}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Queries organization server to authorize this user for this
  * organization and also pulls the organization's environment.
  */
trait OrganizationAuth extends LoggingHelper {

  def organizationClient: Client
  def flowAuth: FlowAuth

  def authorizeOrganization(
    token: ResolvedToken,
    organization: String
  )(
    implicit ec: ExecutionContext
  ): Future[Option[ResolvedToken]] = {
    if (Constants.StopWords.contains(organization)) {
      // javascript sending in 'undefined' or 'null' as session id
      Future.successful(None)
    } else {
      doAuthorizeOrganization(
        token = token,
        organization = organization
      )
    }
  }

  private[this] def doAuthorizeOrganization(
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
            environment = env
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
          environment = Some(orgAuth.environment),
          role = orgAuth.role
        )
      )
    }.recover {
      case io.flow.organization.v0.errors.UnitResponse(code) if code == 401 => None

      case io.flow.organization.v0.errors.UnitResponse(code) => {
        log(token.requestId).
          organization(organization).
          withKeyValue("http_status_code", code).
          warn("Unexpected HTTP Status Code during organization token authorization - request will NOT be authorized")
        None
      }

      case ex: Throwable => {
        log(token.requestId).
          organization(organization).
          withKeyValue("url", organizationClient.baseUrl).
          warn("Error communicating with organization server", ex)
        sys.error("Error communicating with organization server")
      }
    }
  }
}