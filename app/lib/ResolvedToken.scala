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
  role: Option[String] = None,
  sessionId: Option[String] = None
) {

  private[lib] val createdAt = DateTime.now

  def toMap: Map[String, String] = {
    Map(
      "request_id" -> Some(requestId),
      "user_id" -> Some(userId),
      "created_at" -> Some(dateTime.print(createdAt)),
      "session_id" -> sessionId,
      "organization" -> organizationId,
      "partner" -> partnerId,
      "role" -> role,
      "environment" -> environment
    ).flatMap { case (key, value) => value.map { v => key -> v } }
  }
  
}

object ResolvedToken {

  def fromUser(requestId: String, userId: String): ResolvedToken = {
    ResolvedToken(requestId, userId = userId)
  }

}
