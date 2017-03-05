package lib

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat.dateTime

case class ResolvedToken(
  requestId: String,
  userId: Option[String] = None,
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
      "user_id" -> userId,
      "created_at" -> Some(dateTime.print(createdAt)),
      "session_id" -> sessionId,
      "organization" -> organizationId,
      "partner" -> partnerId,
      "role" -> role,
      "environment" -> environment
    ).flatMap { case (key, value) => value.map { v => key -> v } }
  }
  
}