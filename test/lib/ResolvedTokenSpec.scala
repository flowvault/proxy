package lib

import java.util.UUID

import io.flow.common.v0.models.{Environment, Role}
import io.flow.test.utils.FlowPlaySpec
import org.joda.time.format.ISODateTimeFormat.dateTime

class ResolvedTokenSpec extends FlowPlaySpec {

  private[this] val requestId = UUID.randomUUID.toString

  "map contains only values" in {
    val d = ResolvedToken(
      requestId = requestId,
      userId = Some("5"),
      organizationId = None,
      partnerId = None,
      role = None,
      environment = None
    )
    d.toMap must equal(Map("request_id" -> requestId, "user_id" -> "5", "created_at" -> dateTime.print(d.createdAt)))

    val d2 = ResolvedToken(
      requestId = requestId,
      userId = Some("5"),
      organizationId = Some("flow"),
      partnerId = None,
      role = None,
      environment = None
    )
    d2.toMap must equal(Map("request_id" -> requestId, "user_id" -> "5", "created_at" -> dateTime.print(d2.createdAt), "organization" -> "flow"))

    val d3 = ResolvedToken(
      requestId = requestId,
      userId = Some("5"),
      organizationId = None,
      partnerId = Some("flow"),
      role = None,
      environment = None
    )
    d3.toMap must equal(Map("request_id" -> requestId, "user_id" -> "5", "created_at" -> dateTime.print(d3.createdAt), "partner" -> "flow"))
    
    val d4 = ResolvedToken(
      requestId = requestId,
      userId = Some("5"),
      organizationId = Some("flow"),
      partnerId = None,
      role = Some(Role.Member),
      environment = None
    )

    d4.toMap must equal(
      Map(
        "request_id" -> requestId,
        "user_id" -> "5",
        "created_at" -> dateTime.print(d4.createdAt),
        "organization" -> "flow",
        "role" -> "member"
      )
    )

    val d5 = ResolvedToken(
      requestId = requestId,
      userId = Some("5"),
      organizationId = Some("flow"),
      partnerId = None,
      role = Some(Role.Member),
      environment = Some(Environment.Production)
    )

    d5.toMap must equal(
      Map(
        "request_id" -> requestId,
        "user_id" -> "5",
        "created_at" -> dateTime.print(d5.createdAt),
        "organization" -> "flow",
        "role" -> "member",
        "environment" -> "production"
      )
    )
  }

}
