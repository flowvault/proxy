package lib

import io.flow.organization.v0.models.OrganizationAuthorization
import authentikat.jwt.{JwtClaimsSet, JwtHeader, JsonWebToken}
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat.dateTime

object FlowAuthData {

  /**
   * Creates a flow auth data object with only the user id
   */
  def user(userId: String) = FlowAuthData(
    userId = userId,
    organization = None,
    role = None,
    environment = None
  )

  /**
   * Creates a flow auth data object for the user and org
   */
  def org(userId: String, organization: String, orgAuth: OrganizationAuthorization) = FlowAuthData(
    userId = userId,
    organization = Some(organization),
    role = Some(orgAuth.role.toString),
    environment = Some(orgAuth.environment.toString)
  )
  
}
case class FlowAuthData(
  userId: String,
  organization: Option[String],
  role: Option[String],
  environment: Option[String]
) {

  private[lib] val createdAt = new DateTime()

  def toMap(): Map[String, String] = {
    Map(
      "user_id" -> Some(userId),
      "created_at" -> Some(dateTime.print(createdAt)),
      "organization" -> organization,
      "role" -> role,
      "environment" -> environment
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
