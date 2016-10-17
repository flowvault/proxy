package lib

import io.flow.token.v0.models._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat.dateTime
import play.api.Logger

case class ResolvedToken(
  requestId: String,
  userId: String,
  environment: Option[String] = None,
  organizationId: Option[String] = None,
  partnerId: Option[String] = None,
  role: Option[String] = None
) {

  private[lib] val createdAt = new DateTime()

  def toMap(): Map[String, String] = {
    Map(
      "request_id" -> Some(requestId),
      "user_id" -> Some(userId),
      "created_at" -> Some(dateTime.print(createdAt)),
      "organization" -> organizationId,
      "partner" -> partnerId,
      "role" -> role,
      "environment" -> environment
    ).flatMap { case (key, value) => value.map { v => (key -> v)} }
  }
  
}

object ResolvedToken {

  def fromUser(requestId: String, userId: String): ResolvedToken = {
    ResolvedToken(requestId, userId = userId)
  }

  def fromToken(requestId: String, token: TokenReference): Option[ResolvedToken] = {
    token match {
      case t: OrganizationTokenReference => Some(
        ResolvedToken(
          requestId = requestId,
          userId = t.user.id,
          environment = Some(t.environment.toString),
          organizationId = Some(t.organization.id)
        )
      )

      case t: PartnerTokenReference => Some(
        ResolvedToken(
          requestId = requestId,
          userId = t.user.id,
          environment = Some(t.environment.toString),
          partnerId = Some(t.partner.id)
        )
      )

      case TokenReferenceUndefinedType(other) => {
        Logger.warn(s"[proxy] TokenReferenceUndefinedType($other) - proceeding as unauthenticated")
        None
      }
    }
  }

}
