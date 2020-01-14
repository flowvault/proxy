package auth

import java.util.UUID

import io.flow.common.v0.models.{Environment, OrganizationReference, UserReference}
import io.flow.log.RollbarLogger
import io.flow.test.utils.FlowPlaySpec
import io.flow.token.v0.mock
import io.flow.token.v0.models._
import lib.ResolvedToken

object TokenMockClient extends mock.Client

class TokenAuthSpec extends FlowPlaySpec {

  private[this] val tokenTestAuth = new TokenAuth with mock.Client {
    override def tokenClient = TokenMockClient
    override def logger: RollbarLogger = app.injector.instanceOf[RollbarLogger]
  }

  private[this] val requestId = UUID.randomUUID.toString

  "from org" in {
    val token = OrganizationTokenReference(
      id = "0",
      organization = OrganizationReference(id = "tst"),
      environment = Environment.Production,
      user = UserReference("5")
    )

    tokenTestAuth.fromTokenReference(requestId, token) must equal(
      Some(
        ResolvedToken(
          requestId = requestId,
          userId = Some("5"),
          organizationId = Some("tst"),
          partnerId = None,
          role = None,
          environment = Some(Environment.Production)
        )
      )
    )
  }

  "from partner" in {
    val token = PartnerTokenReference(
      id = "0",
      partner = TokenPartnerReference(id = "foo"),
      environment = Environment.Production,
      user = UserReference("5")
    )

    tokenTestAuth.fromTokenReference(requestId, token) must equal(
      Some(
        ResolvedToken(
          requestId = requestId,
          userId = Some("5"),
          organizationId = None,
          partnerId = Some("foo"),
          role = None,
          environment = Some(Environment.Production)
        )
      )
    )
  }
}