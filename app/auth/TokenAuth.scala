package auth

import io.flow.token.v0.interfaces.Client
import io.flow.token.v0.models.{TokenAuthenticationForm, TokenReference}
import lib.Constants

import scala.concurrent.{ExecutionContext, Future}

/**
  * Queries token server to check if the specified token is a known
  * valid token.
  */
trait TokenAuth {

  def tokenClient: Client

  def resolveToken(
    requestId: String, token: String
  )(
    implicit ec: ExecutionContext
  ): Future[Option[TokenReference]] = {
    tokenClient.tokens.postAuthentications(
      TokenAuthenticationForm(token = token),
      requestHeaders = Seq(
        Constants.Headers.FlowRequestId -> requestId
      )
    ).map { tokenReference =>
      Some(tokenReference)

    }.recover {
      case io.flow.token.v0.errors.UnitResponse(404) => {
        None
      }

      case ex: Throwable => {
        sys.error(s"Could not communicate with token server at[${tokenClient.baseUrl}]: $ex")
      }
    }
  }

}