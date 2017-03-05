package auth

import java.util.UUID

import io.flow.common.v0.models.{Environment, OrganizationReference, UserReference}
import io.flow.token.v0.models._
import lib.ResolvedToken
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}

class TokenAuthSpec extends PlaySpec with OneServerPerSuite {

  private[this] val requestId = UUID.randomUUID.toString

  "fromTokenReference as org" in {
    val token = OrganizationTokenReference(
      id = "0",
      organization = OrganizationReference(id = "tst"),
      environment = Environment.Production,
      user = UserReference("5")
    )

    TokenTestAuth.fromTokenReference(requestId, token) must equal(
      Some(
        ResolvedToken(
          requestId = requestId,
          userId = "5",
          organizationId = Some("tst"),
          partnerId = None,
          role = None,
          environment = Some("production")
        )
      )
    )
  }
}