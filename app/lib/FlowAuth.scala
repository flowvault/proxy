package lib

import authentikat.jwt.{JwtClaimsSet, JwtHeader, JsonWebToken}
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat.dateTime

case class FlowAuthData(
  userId: String,
  organization: Option[String],
  role: Option[String]
) {

  private[lib] val createdAt = new DateTime()

  def toMap(): Map[String, String] = {
    Map(
      "user_id" -> Some(userId),
      "created_at" -> Some(dateTime.print(createdAt)),
      "organization" -> organization,
      "role" -> role
    ).flatMap { case (key, value) => value.map { v => (key -> v)} }
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

  /**
    * Returns the string jwt token of the specified auth data.
    */
  def jwt(
    authData: FlowAuthData
  ): String = {
    val claimsSet = JwtClaimsSet(authData.toMap)
    JsonWebToken(header, claimsSet, config.jwtSalt)
  }
  
}
