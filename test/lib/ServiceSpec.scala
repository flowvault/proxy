package lib

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatestplus.play._

class ServiceSpec extends PlaySpec with OneServerPerSuite {

  "organization" in {
    val service = Service(
      "organization",
      "https://organization.api.flow.io",
      routes = Seq(
        Route("GET", "/organizations"),
        Route("GET", "/:organization/memberships")
      )
    )
    val host = service.host

    InternalRoute.Static(host, "GET", "/foo").organization("/foo") must be(None)
    InternalRoute.Static(host, "GET", "/users").organization("/foo") must be(None)
    InternalRoute.Static(host, "GET", "/organization").organization("/foo") must be(None)
    InternalRoute.Static(host, "GET", "/organization/catalog").organization("/foo") must be(None)
    InternalRoute.Static(host, "GET", "/:organization").organization("/flow") must be(Some("flow"))
    InternalRoute.Static(host, "GET", "/:organization/catalog").organization("/flow/catalog") must be(Some("flow"))

    InternalRoute.Dynamic(host, "GET", "/foo/:id").organization("/foo") must be(None)
    InternalRoute.Dynamic(host, "GET", "/users/:id").organization("/foo") must be(None)
    InternalRoute.Dynamic(host, "GET", "/organization/:id").organization("/foo") must be(None)
    InternalRoute.Dynamic(host, "GET", "/organization/catalog/:id").organization("/foo") must be(None)
    InternalRoute.Dynamic(host, "GET", "/:organization/:id").organization("/flow/5") must be(Some("flow"))
    InternalRoute.Dynamic(host, "GET", "/:organization/catalog/:id").organization("/flow/catalog/5") must be(Some("flow"))
  }

}
