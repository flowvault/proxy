package lib

import io.flow.common.v0.models.{Environment, OrganizationReference, Role, UserReference}
import io.flow.organization.v0.models.OrganizationAuthorization
import java.util.UUID

import org.joda.time.format.ISODateTimeFormat.dateTime
import org.scalatest._
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._

import io.flow.common.v0.models.{Environment, Role}
import io.flow.token.v0.models._

class FlowAuthSpec extends PlaySpec with OneServerPerSuite {

  private[this] val requestId = UUID.randomUUID.toString

  "ResolvedToken.token" in {
    ResolvedToken.fromUser("rid", userId = "5") must be(
      ResolvedToken(
        requestId = requestId,
        userId = "5",
        organizationId = None,
        partnerId = None,
        role = None,
        environment = None
      )
    )
  }

  "ResolvedToken.org" in {
    val token = OrganizationTokenReference(
      id = "0",
      organization = OrganizationReference(id = "tst"),
      environment = Environment.Production,
      user = UserReference("5")
    )

    ResolvedToken.fromToken("rid", token) must be(
      ResolvedToken(
        requestId = "0",
        userId = "5",
        organizationId = Some("tst"),
        role = None,
        environment = Some("production")
      )
    )
  }

  "map contains only values" in {
    val d = ResolvedToken(
      requestId = requestId,
      userId = "5",
      organizationId = None,
      partnerId = None,
      role = None,
      environment = None
    )
    d.toMap must be(Map("request_id" -> requestId, "user_id" -> "5", "created_at" -> dateTime.print(d.createdAt)))

    val d2 = ResolvedToken(
      requestId = requestId,
      userId = "5",
      organizationId = Some("flow"),
      partnerId = None,
      role = None,
      environment = None
    )
    d2.toMap must be(Map("request_id" -> requestId, "user_id" -> "5", "created_at" -> dateTime.print(d2.createdAt), "organization" -> "flow"))

    val d3 = ResolvedToken(
      requestId = requestId,
      userId = "5",
      organizationId = None,
      partnerId = Some("flow"),
      role = None,
      environment = None
    )
    d3.toMap must be(Map("request_id" -> requestId, "user_id" -> "5", "created_at" -> dateTime.print(d2.createdAt), "partner" -> "flow"))
    
    val d4 = ResolvedToken(
      requestId = requestId,
      userId = "5",
      organizationId = Some("flow"),
      partnerId = None,
      role = Some("member"),
      environment = None
    )

    d4.toMap must be(
      Map(
        "request_id" -> requestId,
        "user_id" -> "5",
        "created_at" -> dateTime.print(d3.createdAt),
        "organization" -> "flow",
        "role" -> "member"
      )
    )

    val d5 = ResolvedToken(
      requestId = requestId,
      userId = "5",
      organizationId = Some("flow"),
      partnerId = None,
      role = Some("member"),
      environment = Some("production")
    )

    d5.toMap must be(
      Map(
        "request_id" -> requestId,
        "user_id" -> "5",
        "created_at" -> dateTime.print(d4.createdAt),
        "organization" -> "flow",
        "role" -> "member",
        "environment" -> "production"
      )
    )
  }

}
