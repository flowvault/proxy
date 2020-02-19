package lib

import authentikat.jwt.{JwtClaimsSet, JwtHeader, JsonWebToken}
import javax.inject.{Inject, Singleton}

object FlowAuth {

  def headersFromRequestId(requestId: String): Seq[(String, String)] = {
    Seq(
      Constants.Headers.FlowRequestId -> requestId
    )
  }

}

/**
  * Defines the data that goes into the flow auth set by the proxy server.
  */
@Singleton
final class FlowAuth @Inject () (
  config: Config
) {

  private[this] val header = JwtHeader("HS256")

  def headers(token: ResolvedToken): Seq[(String, String)] = {
    FlowAuth.headersFromRequestId(token.requestId) ++ Seq(
      Constants.Headers.FlowAuth -> jwt(token)
    )
  }

  /**
    * Returns the string jwt token of the specified auth data.
    */
  def jwt(
    token: ResolvedToken
  ): String = {
    val claimsSet = JwtClaimsSet(token.toMap)
    JsonWebToken(header, claimsSet, config.jwtSalt)
  }
  
}
