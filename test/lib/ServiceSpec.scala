package lib

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatestplus.play._

class ServiceSpec extends PlaySpec with OneServerPerSuite {

  "organization" in {
    Route("GET", "/foo").organization("/foo") must be(None)
    Route("GET", "/users").organization("/foo") must be(None)
    Route("GET", "/organization").organization("/foo") must be(None)
    Route("GET", "/organization/catalog").organization("/foo") must be(None)
    Route("GET", "/:organization").organization("/flow") must be(Some("flow"))
    Route("GET", "/:organization/catalog").organization("/flow/catalog") must be(Some("flow"))
    Route("GET", "/:organization/currency/rates").organization("/test/currency/rates") must be(Some("test"))
    Route("GET", "/internal/currency/rates").organization("/internal/currency/rates") must be(Some("flow"))
    Route("GET", "/foo/:id").organization("/foo") must be(None)
    Route("GET", "/users/:id").organization("/foo") must be(None)
    Route("GET", "/organization/:id").organization("/foo") must be(None)
    Route("GET", "/organization/catalog/:id").organization("/foo") must be(None)
    Route("GET", "/:organization/:id").organization("/flow/5") must be(Some("flow"))
    Route("GET", "/:organization/catalog/:id").organization("/flow/catalog/5") must be(Some("flow"))
  }

}
