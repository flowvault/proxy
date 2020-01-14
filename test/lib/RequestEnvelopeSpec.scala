package lib

import cats.data.NonEmptyChain
import cats.data.Validated.Invalid
import helpers.BasePlaySpec
import play.api.libs.json.{JsArray, JsBoolean, JsNull, Json}
import play.api.mvc.Headers

class RequestEnvelopeSpec extends BasePlaySpec {

  private[this] def validateHeaders(envelopeHeaders: Map[String, Seq[String]], requestHeaders: Headers): Headers = {
    val js = Json.obj(
      "method" -> "POST",
      "headers" -> envelopeHeaders,
    )
    validOrErrors(RequestEnvelope.validate(js, requestHeaders)).headers
  }

  "validateMethod" in {
    RequestEnvelope.validateMethod(Json.obj()) must equal(
      Invalid(NonEmptyChain.one("Request envelope field 'method' is required"))
    )

    RequestEnvelope.validateMethod(Json.obj("method" -> " ")) must equal(
      Invalid(NonEmptyChain.one("Request envelope field 'method' must be one of GET, POST, PUT, PATCH, DELETE, HEAD, CONNECT, OPTIONS, TRACE"))
    )

    RequestEnvelope.validateMethod(Json.obj("method" -> "foo")) must equal(
      Invalid(NonEmptyChain.one("Request envelope field 'method' must be one of GET, POST, PUT, PATCH, DELETE, HEAD, CONNECT, OPTIONS, TRACE"))
    )


    validOrErrors(RequestEnvelope.validateMethod(Json.obj("method" -> "post"))) must equal(
      Method.Post
    )
    Method.all.forall { m =>
      RequestEnvelope.validateMethod(Json.obj("method" -> m.toString)).isValid
    } must be(true)
  }

  "validateHeaders" in {
    validOrErrors(RequestEnvelope.validateHeaders(Json.obj())) must equal(Map.empty)
    validOrErrors(RequestEnvelope.validateHeaders(Json.obj("headers" -> Json.obj()))) must equal(
      Map.empty
    )
      validOrErrors(RequestEnvelope.validateHeaders(Json.obj("headers" -> Json.obj(
      "foo" -> JsArray(Nil)
    )))) must equal(
      Map("foo" -> Nil)
    )

    validOrErrors(RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> Json.obj(
        "foo" -> Seq("bar")
      )
    ))) must equal(
      Map("foo" -> Seq("bar"))
    )

    validOrErrors(RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> Json.obj(
        "foo" -> Seq("bar"),
        "a" -> Seq("b"),
      )
    ))) must equal(
      Map("foo" -> Seq("bar"), "a" -> Seq("b"))
    )

    validOrErrors(RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> Json.obj(
        "foo" -> Seq("bar", "baz")
      )
    ))) must equal(
      Map("foo" -> Seq("bar", "baz"))
    )

    validOrErrors(RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> Json.obj(
        "foo" -> "bar"
      )
    ))) must equal(
      Map("foo" -> Seq("bar"))
    )

    RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> "a"
    )) must equal(
      Invalid(NonEmptyChain.one("Request envelope field 'headers' must be an object"))
    )
  }

  "validateHeaders preserves only whitelisted headers" in {
    validateHeaders(Map.empty, Headers())  must equal(Headers())
    validateHeaders(Map.empty, Headers(("foo", "bar")))  must equal(Headers())
    validateHeaders(Map.empty, Headers(("CF-Connecting-IP", "1.2.3.4")))  must equal(
      Headers(("CF-Connecting-IP", "1.2.3.4"))
    )
  }

  "validateHeaders prefers envelope headers to request headers" in {
    validateHeaders(Map("CF-Connecting-IP" -> Seq("4.5.6.7")), Headers(("CF-Connecting-IP", "1.2.3.4")))  must equal(
      Headers(("CF-Connecting-IP", "4.5.6.7"))
    )

    validateHeaders(Map(
      "foo" -> Seq("bar"),
      "CF-Connecting-IP" -> Seq("4.5.6.7"),
    ), Headers(
      ("CF-Connecting-IP", "1.2.3.4"))
    )  must equal(
      Headers(("foo", "bar"), ("CF-Connecting-IP", "4.5.6.7"))
    )
  }

  "validateBody" in {
    validOrErrors(RequestEnvelope.validateBody(JsNull)) must be(None)
    validOrErrors(RequestEnvelope.validateBody(Json.obj())) must be(None)
    validOrErrors(RequestEnvelope.validateBody(Json.obj("body" -> Json.obj()))) must equal(Some(
      ProxyRequestBody.Json(Json.obj())
    ))
    validOrErrors(RequestEnvelope.validateBody(Json.obj("body" -> Json.obj("a" -> "b")))) must equal(Some(
      ProxyRequestBody.Json(Json.obj("a" -> "b"))
    ))
    validOrErrors(RequestEnvelope.validateBody(Json.obj("body" -> JsBoolean(true)))) must equal(Some(
      ProxyRequestBody.Json(JsBoolean(true))
    ))
  }
}
