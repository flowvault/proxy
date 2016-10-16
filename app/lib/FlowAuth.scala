package lib

import authentikat.jwt.{JwtClaimsSet, JwtHeader, JsonWebToken}
import javax.inject.{Inject, Singleton}

/**
  * Defines the data that goes into the flow auth set by the proxy server.
  */
@Singleton
final class FlowAuth @Inject () (
  config: Config
) {

  private[this] val header = JwtHeader("HS256")

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
