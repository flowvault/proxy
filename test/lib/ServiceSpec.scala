package lib

import helpers.BasePlaySpec

class ServiceSpec extends BasePlaySpec {

  "organization" in {
    Route(Method.Get, "/foo").organization("/foo") must be(None)
    Route(Method.Get, "/users").organization("/foo") must be(None)
    Route(Method.Get, "/organization").organization("/foo") must be(None)
    Route(Method.Get, "/organization/catalog").organization("/foo") must be(None)
    Route(Method.Get, "/:organization").organization("/flow") must be(Some("flow"))
    Route(Method.Get, "/:organization/catalog").organization("/flow/catalog") must be(Some("flow"))
    Route(Method.Get, "/:organization/currency/rates").organization("/test/currency/rates") must be(Some("test"))
    Route(Method.Get, "/internal/currency/rates").organization("/internal/currency/rates") must be(Some("flow"))
    Route(Method.Get, "/foo/:id").organization("/foo") must be(None)
    Route(Method.Get, "/users/:id").organization("/foo") must be(None)
    Route(Method.Get, "/organization/:id").organization("/foo") must be(None)
    Route(Method.Get, "/organization/catalog/:id").organization("/foo") must be(None)
    Route(Method.Get, "/:organization/:id").organization("/flow/5") must be(Some("flow"))
    Route(Method.Get, "/:organization/catalog/:id").organization("/flow/catalog/5") must be(Some("flow"))
  }

  "partner" in {
    Route(Method.Get, "/foo").partner("/partners/foo") must be(None)
    Route(Method.Get, "/users").partner("/partners/foo") must be(None)
    Route(Method.Get, "/partners").partner("/partners/foo") must be(None)
    Route(Method.Get, "/partners").partner("/partners/foo") must be(None)
    Route(Method.Get, "/partners/catalog").partner("/partners/foo") must be(None)
    Route(Method.Get, "/partners/:partner").partner("/partners/flow") must be(Some("flow"))
    Route(Method.Get, "/partners/:partner/catalog").partner("/partners/flow/catalog") must be(Some("flow"))
    Route(Method.Get, "/partners/:partner/labels").partner("/partners/ql/labels") must be(Some("ql"))
    Route(Method.Get, "/internal/currency/rates").partner("/internal/currency/rates") must be(None)
    Route(Method.Get, "/partners/foo/:id").partner("/partners/foo") must be(None)
    Route(Method.Get, "/users/:id").partner("/partners/foo") must be(None)
    Route(Method.Get, "/partners/partner/:id").partner("/partners/foo") must be(None)
    Route(Method.Get, "/partners/partner/catalog/:id").partner("/partners/foo") must be(None)
    Route(Method.Get, "/partners/:partner/:id").partner("/partners/flow/5") must be(Some("flow"))
    Route(Method.Get, "/partners/:partner/catalog/:id").partner("/partners/flow/catalog/5") must be(Some("flow"))
  }

}
