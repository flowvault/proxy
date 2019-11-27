package lib

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsBoolean, JsNull, Json}
import play.api.mvc.Headers

class RequestEnvelopeSpec extends PlaySpec {

  private[this] def validateHeaders(envelopeHeaders: Map[String, Seq[String]], requestHeaders: Headers): Headers = {
    val js = Json.obj(
      "method" -> "POST",
      "headers" -> envelopeHeaders,
    )
    RequestEnvelope.validate(js, requestHeaders).right.get.headers
  }

  "validateMethod" in {
    RequestEnvelope.validateMethod(Json.obj()) must equal(
      Left(Seq("Request envelope field 'method' is required"))
    )

    RequestEnvelope.validateMethod(Json.obj("method" -> " ")) must equal(
      Left(Seq("Request envelope field 'method' must be one of GET, POST, PUT, PATCH, DELETE, HEAD, CONNECT, OPTIONS, TRACE"))
    )

    RequestEnvelope.validateMethod(Json.obj("method" -> "foo")) must equal(
      Left(Seq("Request envelope field 'method' must be one of GET, POST, PUT, PATCH, DELETE, HEAD, CONNECT, OPTIONS, TRACE"))
    )


    RequestEnvelope.validateMethod(Json.obj("method" -> "post")) must equal(
      Right(Method.Post)
    )
    Method.all.forall { m =>
      RequestEnvelope.validateMethod(Json.obj("method" -> m.toString)).isRight
    } must be(true)
  }

  "validateHeaders" in {
    RequestEnvelope.validateHeaders(Json.obj()) must equal(Right(Map.empty))
    RequestEnvelope.validateHeaders(Json.obj("headers" -> Json.obj())) must equal(
      Right(Map.empty)
    )
    RequestEnvelope.validateHeaders(Json.obj("headers" -> Json.obj(
      "foo" -> JsArray(Nil)
    ))) must equal(
      Right(Map("foo" -> Nil))
    )

    RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> Json.obj(
        "foo" -> Seq("bar")
      )
    )) must equal(
      Right(Map("foo" -> Seq("bar")))
    )

    RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> Json.obj(
        "foo" -> Seq("bar"),
        "a" -> Seq("b"),
      )
    )) must equal(
      Right(Map("foo" -> Seq("bar"), "a" -> Seq("b")))
    )

    RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> Json.obj(
        "foo" -> Seq("bar", "baz")
      )
    )) must equal(
      Right(Map("foo" -> Seq("bar", "baz")))
    )

    RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> Json.obj(
        "foo" -> "bar"
      )
    )) must equal(
      Right(Map("foo" -> Seq("bar")))
    )

    RequestEnvelope.validateHeaders(Json.obj(
      "headers" -> "a"
    )) must equal(
      Left(Seq("Request envelope field 'headers' must be an object"))
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
    RequestEnvelope.validateBody(JsNull) must be(Right(None))
    RequestEnvelope.validateBody(Json.obj()) must be(Right(None))
    RequestEnvelope.validateBody(Json.obj("body" -> Json.obj())) must equal(Right(Some(
      ProxyRequestBody.Json(Json.obj())
    )))
    RequestEnvelope.validateBody(Json.obj("body" -> Json.obj("a" -> "b"))) must equal(Right(Some(
      ProxyRequestBody.Json(Json.obj("a" -> "b"))
    )))
    RequestEnvelope.validateBody(Json.obj("body" -> JsBoolean(true))) must equal(Right(Some(
      ProxyRequestBody.Json(JsBoolean(true))
    )))
  }
}
