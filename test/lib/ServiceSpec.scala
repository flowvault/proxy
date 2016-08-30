package lib

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatestplus.play._

class ServiceSpec extends PlaySpec with OneServerPerSuite {

  "organization" in {
    val host = "https://organization.api.flow.io"

    Route("GET", "/foo", host).organization("/foo") must be(None)
    Route("GET", "/users", host).organization("/foo") must be(None)
    Route("GET", "/organization", host).organization("/foo") must be(None)
    Route("GET", "/organization/catalog", host).organization("/foo") must be(None)
    Route("GET", "/:organization", host).organization("/flow") must be(Some("flow"))
    Route("GET", "/:organization/catalog", host).organization("/flow/catalog") must be(Some("flow"))
    Route("GET", "/:organization/currency/rates", host).organization("/test/currency/rates") must be(Some("test"))
    Route("GET", "/internal/currency/rates", host).organization("/internal/currency/rates") must be(Some("flow"))
    Route("GET", "/foo/:id", host).organization("/foo") must be(None)
    Route("GET", "/users/:id", host).organization("/foo") must be(None)
    Route("GET", "/organization/:id", host).organization("/foo") must be(None)
    Route("GET", "/organization/catalog/:id", host).organization("/foo") must be(None)
    Route("GET", "/:organization/:id", host).organization("/flow/5") must be(Some("flow"))
    Route("GET", "/:organization/catalog/:id", host).organization("/flow/catalog/5") must be(Some("flow"))
  }

}
