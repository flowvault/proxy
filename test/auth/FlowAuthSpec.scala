package auth

import helpers.BasePlaySpec
import lib.{Config, Constants, FlowAuth, ResolvedToken}
import org.scalatest.TryValues
import pdi.jwt.{JwtAlgorithm, JwtJson}

class FlowAuthSpec extends BasePlaySpec with TryValues {

  private val flowAuth = app.injector.instanceOf[FlowAuth]
  private val jwtKey = app.injector.instanceOf[Config].jwtSalt

  "FlowAuth" should {

    "create jwt" in {
      val token = ResolvedToken(
        requestId = "123",
        sessionId = Some("abc")
      )
      val jwt = flowAuth.jwt(token)

      val decoded = JwtJson.decodeJson(token = jwt, key = jwtKey, algorithms = JwtAlgorithm.allHmac()).success.value

      (decoded \ "request_id").as[String] mustBe "123"
      (decoded \ "session").as[String] mustBe "abc"
    }

    "create headers" in {
      val token = ResolvedToken(
        requestId = "123",
        sessionId = Some("abc")
      )
      val headers = flowAuth.headers(token)

      headers.toMap mustBe Map(
        Constants.Headers.FlowRequestId -> "123",
        Constants.Headers.FlowAuth -> flowAuth.jwt(token)
      )
    }

  }

}
