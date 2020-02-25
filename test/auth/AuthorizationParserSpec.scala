package auth

import helpers.BasePlaySpec
import lib.Authorization._
import lib.{AuthorizationParser, Config}
import org.apache.commons.codec.binary.{Base64, StringUtils}
import pdi.jwt.JwtAlgorithm._
import pdi.jwt.{Jwt, JwtClaim, JwtHeader}

class AuthorizationParserSpec extends BasePlaySpec {

  private val authParser = app.injector.instanceOf[AuthorizationParser]
  private val jwtKey = app.injector.instanceOf[Config].jwtSalt

  private def createUserClaim(id: String) = JwtClaim(s"""{"id": "$id"}""")
  private def createSessionClaim(id: String) = JwtClaim(s"""{"session": "$id"}""")
  private def createCustomerClaim(customerId: String, sessionId: String) =
    JwtClaim(s"""{"session": "$sessionId", "customer": "$customerId"}""")

  "AuthorizationParser" should {

    "Bearer - decode user" in {
      // generated in jwt.io
      // header:
      // {
      //   "alg": "HS256",
      //   "typ": "JWT"
      // }
      // payload:
      // {
      //   "id": "test_id",
      //   "email": "test@flow.io",
      //   "iat": 1482652851,
      //   "exp": 1483257651,
      //   "iss": "https://console.flow.io",
      //   "sub": "test@flow.io"
      // }
      // signature: test

      val value = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6InRlc3RfaWQiLCJlbWFpbCI6InRlc3RAZmxvdy5pbyIsImlhdCI6MTU4MjY1Mjg1MSwiZXhwIjoxNTgzMjU3NjUxLCJpc3MiOiJodHRwczovL2NvbnNvbGUuZmxvdy5pbyIsInN1YiI6InRlc3RAZmxvdy5pbyJ9.xGgsu8w8ycaz6mDEjNuorFEVDNyuM3Ya562Nih_CU6g"
      val header: String = s"Bearer $value"

      authParser.parse(header) mustBe User("test_id")
    }

    "Bearer - decode any user" in {
      val value = Jwt.encode(header = JwtHeader(HS256), claim = createUserClaim("any_user"), key = jwtKey)
      val header: String = s"Bearer $value"

      authParser.parse(header) mustBe User("any_user")
    }

    "Bearer - decode a session" in {
      val value = Jwt.encode(header = JwtHeader(HS256), claim = createSessionClaim("a_session"), key = jwtKey)
      val header: String = s"Bearer $value"

      authParser.parse(header) mustBe Session("a_session")
    }

    "Bearer - decode a customer" in {
      val claim = createCustomerClaim(customerId = "a_customer", sessionId = "some_session")
      val value = Jwt.encode(header = JwtHeader(HS256), claim = claim, key = jwtKey)
      val header: String = s"Bearer $value"

      authParser.parse(header) mustBe Customer("a_customer", "some_session")
    }

    "Bearer - invalid user" in {
      // customer id but no session
      val value = Jwt.encode(header = JwtHeader(HS256), claim = JwtClaim(s"""{"no_id": "no_id"}"""), key = jwtKey)
      val header: String = s"Bearer $value"

      authParser.parse(header) mustBe InvalidBearer
    }

    "Bearer - invalid customer" in {
      // customer id but no session
      val value = Jwt.encode(header = JwtHeader(HS256), claim = JwtClaim(s"""{"customer": "a_customer"}"""), key = jwtKey)
      val header: String = s"Bearer $value"

      authParser.parse(header) mustBe InvalidJwt(Seq("customer", "session"))
    }

    "Bearer - invalid key" in {
      val value = Jwt.encode(header = JwtHeader(HS256), claim = createUserClaim("any_user"), key = "wrong_key")
      val header: String = s"Bearer $value"

      authParser.parse(header) mustBe InvalidBearer
    }

    "Unrecognized - no prefix" in {
      // valid bearer value, but no prefix
      val value = Jwt.encode(header = JwtHeader(HS256), claim = createUserClaim("any_user"), key = jwtKey)

      authParser.parse(value) mustBe Unrecognized
    }

    "Unrecognized - too many values" in {
      // add more than the value
      val value = Jwt.encode(header = JwtHeader(HS256), claim = createUserClaim("any_user"), key = jwtKey)
      val header: String = s"Bearer $value some_more"

      authParser.parse(header) mustBe Unrecognized
    }

    "Unrecognized - prefix only" in {
      authParser.parse("Bearer  ") mustBe Unrecognized
      authParser.parse("Basic  ") mustBe Unrecognized
      authParser.parse("Session  ") mustBe Unrecognized
    }

    "Session" in {
      val header: String = s"Session some_session"

      authParser.parse(header) mustBe Session("some_session")
    }

    "Basic - valid" in {
      val value = new String(Base64.encodeBase64(StringUtils.getBytesUsAscii("token:")))
      val header: String = s"Basic $value"

      authParser.parse(header) mustBe Token("token")
    }

    "Basic - no colon" in {
      // note there is no ":"
      val value = new String(Base64.encodeBase64(StringUtils.getBytesUsAscii("token")))
      val header: String = s"Basic $value"

      authParser.parse(header) mustBe Token("token")
    }

  }

}
