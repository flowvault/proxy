package lib

import io.flow.common.v0.models.{Environment, Role}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat.dateTime

case class ResolvedToken(
  requestId: String,
  userId: Option[String] = None,
  environment: Option[Environment] = None,
  organizationId: Option[String] = None,
  partnerId: Option[String] = None,
  role: Option[Role] = None,
  sessionId: Option[String] = None,
  customerNumber: Option[String] = None
) {
  private[lib] val createdAt = DateTime.now

  def toMap: Map[String, String] = {
    Map(
      "request_id" -> Some(requestId),
      "user_id" -> userId,
      "created_at" -> Some(dateTime.print(createdAt)),
      "session" -> sessionId,
      "organization" -> organizationId,
      "partner" -> partnerId,
      "role" -> role.map(_.toString),
      "environment" -> environment.map(_.toString),
      "customer" -> customerNumber
    ).flatMap { case (key, value) => value.map { v => key -> v } }
  }
  
}