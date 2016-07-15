package lib

import io.flow.common.v0.models.{Environment, Role}
import io.flow.organization.v0.models.OrganizationAuthorization

import org.joda.time.format.ISODateTimeFormat.dateTime
import org.scalatest._
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._

class FlowAuthSpec extends PlaySpec with OneServerPerSuite {

  "FlowAuthData.user" in {
    FlowAuthData.user("5") must be(
      FlowAuthData(
        userId = "5",
        organization = None,
        role = None,
        environment = None
      )
    )
  }

  "FlowAuthData.org" in {
    FlowAuthData.org("5", "tst", OrganizationAuthorization(role = Role.Member, environment = Environment.Production)) must be(
      FlowAuthData(
        userId = "5",
        organization = Some("tst"),
        role = Some("member"),
        environment = Some("production")
      )
    )
  }

  "map contains only values" in {
    val d = FlowAuthData(
      userId = "5",
      organization = None,
      role = None,
      environment = None
    )
    d.toMap must be(Map("user_id" -> "5", "created_at" -> dateTime.print(d.createdAt)))

    val d2 = FlowAuthData(
      userId = "5",
      organization = Some("flow"),
      role = None,
      environment = None
    )
    d2.toMap must be(Map("user_id" -> "5", "created_at" -> dateTime.print(d2.createdAt), "organization" -> "flow"))

    val d3 = FlowAuthData(
      userId = "5",
      organization = Some("flow"),
      role = Some("member"),
      environment = None
    )

    d3.toMap must be(
      Map(
        "user_id" -> "5",
        "created_at" -> dateTime.print(d3.createdAt),
        "organization" -> "flow",
        "role" -> "member"
      )
    )

    val d4 = FlowAuthData(
      userId = "5",
      organization = Some("flow"),
      role = Some("member"),
      environment = Some("production")
    )

    d4.toMap must be(
      Map(
        "user_id" -> "5",
        "created_at" -> dateTime.print(d4.createdAt),
        "organization" -> "flow",
        "role" -> "member",
        "environment" -> "production"
      )
    )
  }

}
